<?php
declare(strict_types=1);
/**
 * LoyPOS — devices & staff admin (Tier 2 per-device auth)
 *
 *   https://novelsapps.com.et/api/v1/devices.php
 *
 * Sign in with an owner or manager account (created here on first run) to:
 *   - review paired terminals (name, who paired them, last seen)
 *   - revoke / reactivate / delete a device's token in one click
 *   - manage staff accounts (owner / manager / cashier)
 *   - switch "require device auth" on/off
 *
 * The installer's setup token (?token=...) still opens this page without an
 * account: use it ONCE to create the first owner account, and later as the
 * recovery key if the owner password is ever lost. Everything else runs on
 * normal account sign-in with PHP sessions + CSRF protection.
 *
 * Idempotent and read-only by default: nothing changes unless you submit a form.
 */

// Setup token: the REAL token lives in config.php (key: setup_token, never
// committed — rotate it via Plesk File Manager). The built-in const is only
// the fallback used before a config.php exists.
const SETUP_TOKEN_FALLBACK = '67566d1660ab1f7692d250a9';

// ── Session (hardened cookie) ───────────────────────────────────────────────
session_set_cookie_params([
    'lifetime' => 0,
    'path'     => '/',
    'secure'   => !empty($_SERVER['HTTPS']) && $_SERVER['HTTPS'] !== 'off',
    'httponly' => true,
    'samesite' => 'Lax',
]);
session_start();

function e(?string $s): string {
    return htmlspecialchars((string)$s, ENT_QUOTES, 'UTF-8');
}

function ago(?int $ms): string {
    if ($ms === null || $ms <= 0) {
        return 'never';
    }
    $d = (int)round(microtime(true) * 1000) - $ms;
    if ($d < 0) {
        return 'just now';
    }
    foreach ([['d', 86400000], ['h', 3600000], ['m', 60000]] as [$u, $div]) {
        if ($d >= $div) {
            return intdiv($d, $div) . $u . ' ago';
        }
    }
    return 'just now';
}

/** Sliding-window throttle (10 requests / minute per IP + scope), file-based. */
function too_many_attempts(string $scope): bool {
    $key = 'loypos_' . $scope . '_' . hash('sha256', (string)($_SERVER['REMOTE_ADDR'] ?? 'unknown'));
    $file = sys_get_temp_dir() . '/' . $key;
    $nowMs = (int)round(microtime(true) * 1000);
    $hits = is_file($file) ? (json_decode((string)file_get_contents($file), true) ?: []) : [];
    $hits = array_values(array_filter($hits, static fn($t) => $t > $nowMs - 60000));
    if (count($hits) >= 10) {
        return true;
    }
    $hits[] = $nowMs;
    @file_put_contents($file, json_encode($hits), LOCK_EX);
    return false;
}

$setupTokenInput = (string)($_REQUEST['token'] ?? '');

function fetch_session_user(PDO $pdo): ?array {
    if (!isset($_SESSION['devadmin_uid'])) {
        return null;
    }
    try {
        $q = $pdo->prepare('SELECT id, email, name, role, status FROM users WHERE id = :id LIMIT 1');
        $q->execute([':id' => (int)$_SESSION['devadmin_uid']]);
        $row = $q->fetch();
        if ($row && $row['status'] === 'active') {
            return $row;
        }
    } catch (Throwable $ex) {
        // users table missing or row gone — fall through to sign-out
    }
    unset($_SESSION['devadmin_uid']);
    return null;
}

// ── Database ────────────────────────────────────────────────────────────────
$configPath = __DIR__ . '/config.php';
$cfg = null;
$pdo = null;
$dbError = '';
try {
    $maybe = require $configPath;
    $cfg = is_array($maybe) ? $maybe : null;
    if ($cfg === null) {
        $dbError = 'config.php did not return an array — run install.php first.';
    } else {
        $dsn = sprintf('mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4',
            $cfg['db_host'] ?? 'localhost', (int)($cfg['db_port'] ?? 3306), $cfg['db_name'] ?? '');
        $pdo = new PDO($dsn, (string)($cfg['db_user'] ?? ''), (string)($cfg['db_pass'] ?? ''), [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES => false,
        ]);
    }
} catch (Throwable $ex) {
    $dbError = $ex->getMessage();
}

if ($dbError !== '' || $pdo === null) {
    header('Content-Type: text/html; charset=utf-8');
    echo '<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">'
        . '<meta name="robots" content="noindex,nofollow"><title>LoyPOS devices</title>'
        . '<style>body{font-family:system-ui,sans-serif;background:#0f172a;color:#e2e8f0;margin:0;padding:2rem}'
        . '.card{max-width:680px;margin:2rem auto;background:#1e293b;border-radius:12px;padding:2rem}</style></head>'
        . '<body><div class="card"><h1>LoyPOS device admin</h1><p style="color:#f87171">Database unavailable: '
        . e($dbError) . '</p><p>Run <a style="color:#4ade80" href="install.php?token=' . e(SETUP_TOKEN_FALLBACK) . '">install.php</a> first — '
        . 'it creates the devices and users tables (migration steps 002/003).</p></div></body></html>';
    exit;
}

// Setup token resolution + admin-session grant. The setup token is the master
// key (same secret as install.php): use it once in the browser and this
// session gets admin rights — bootstrap the first owner, or recover by
// resetting the owner password later. Forms only carry the CSRF token
// afterwards, never the secret itself.
$setupToken = trim((string)($cfg['setup_token'] ?? '')) ?: SETUP_TOKEN_FALLBACK;
$setupOk = $setupTokenInput !== '' && hash_equals($setupToken, $setupTokenInput);
if ($setupOk && empty($_SESSION['setup_admin'])) {
    session_regenerate_id(true);
    $_SESSION['setup_admin'] = true;
}

// ── State ───────────────────────────────────────────────────────────────────
$now = (int)round(microtime(true) * 1000);
$actionMsg = '';
$actionErr = '';

if (empty($_SESSION['csrf'])) {
    $_SESSION['csrf'] = bin2hex(random_bytes(16));
}
$csrfOk = isset($_POST['csrf']) && hash_equals($_SESSION['csrf'], (string)$_POST['csrf']);

// users table present? (created by migration 003_account_auth)
$userCount = 0;
$usersMissing = false;
try {
    $userCount = (int)$pdo->query('SELECT COUNT(*) FROM users')->fetchColumn();
} catch (Throwable $ex) {
    $usersMissing = true;
}

// Signed-in staff member (if any)
$sessionUser = fetch_session_user($pdo);
if ($sessionUser === null && isset($_SESSION['devadmin_uid'])) {
    $actionErr = 'Your account is no longer active — signed out.';
}
$isOwner = $sessionUser !== null && $sessionUser['role'] === 'owner';
$isStaff = $sessionUser !== null && in_array($sessionUser['role'], ['owner', 'manager'], true);
$setupAdmin     = !empty($_SESSION['setup_admin']);
$admin          = $isStaff || $setupAdmin;   // setup token = master key (bootstrap + recovery)
$canManageUsers = $isOwner || $setupAdmin;   // owner-only actions

// ── POST actions ────────────────────────────────────────────────────────────
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = (string)($_POST['action'] ?? '');
    try {
        if ($action === 'login') {
            if (too_many_attempts('devlogin')) {
                throw new RuntimeException('Too many sign-in attempts from this address — try again in a minute.');
            }
            if (!$csrfOk) {
                throw new RuntimeException('Session expired — try again.');
            }
            $email = strtolower(trim((string)($_POST['email'] ?? '')));
            $pass  = (string)($_POST['password'] ?? '');
            $u = null;
            try {
                $q = $pdo->prepare('SELECT id, password_hash, role, status FROM users WHERE email = :e LIMIT 1');
                $q->execute([':e' => $email]);
                $u = $q->fetch();
            } catch (Throwable $ex) {
                $u = false;
            }
            $ok = $u && password_verify($pass, (string)$u['password_hash'])
                && $u['status'] === 'active'
                && in_array($u['role'], ['owner', 'manager'], true);
            if (!$ok) {
                usleep(400000); // slow down guessing without revealing which part failed
                throw new RuntimeException('Invalid email or password (only owner/manager accounts can sign in here).');
            }
            session_regenerate_id(true);
            $_SESSION['devadmin_uid'] = (int)$u['id'];
            $actionMsg = 'Signed in as ' . $email . '.';
        } elseif ($action === 'logout') {
            $_SESSION = [];
            session_destroy();
            // start a clean session so the login form gets a fresh CSRF token
            session_start();
            $_SESSION['csrf'] = bin2hex(random_bytes(16));
            $actionMsg = 'Signed out.';
        } elseif ($action === 'create_owner') {
            if (!$setupOk && empty($_SESSION['setup_admin'])) {
                throw new RuntimeException('Owner bootstrap requires the setup token (?token=...).');
            }
            if ($userCount > 0) {
                throw new RuntimeException('An owner account already exists — sign in instead.');
            }
            if (!$csrfOk) {
                throw new RuntimeException('Session expired — try again.');
            }
            $email = strtolower(trim((string)($_POST['email'] ?? '')));
            $name  = mb_substr(trim((string)($_POST['name'] ?? '')), 0, 100);
            $pass  = (string)($_POST['password'] ?? '');
            $pass2 = (string)($_POST['password2'] ?? '');
            if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
                throw new RuntimeException('Enter a valid email address.');
            }
            if (strlen($pass) < 8) {
                throw new RuntimeException('Password must be at least 8 characters.');
            }
            if ($pass !== $pass2) {
                throw new RuntimeException('Passwords do not match.');
            }
            $pdo->prepare("INSERT INTO users (email, password_hash, name, role, status, created_ms)
                           VALUES (:e, :p, :n, 'owner', 'active', :c)")
                ->execute([':e' => $email, ':p' => password_hash($pass, PASSWORD_DEFAULT),
                           ':n' => $name !== '' ? $name : null, ':c' => $now]);
            $userCount = 1;
            $actionMsg = 'Owner account created for ' . $email . ' — sign in with it from now on, and use it on POS terminals (Settings → Sign In & Pair This Device).';
        } elseif ($admin && $csrfOk) {
            switch ($action) {
                case 'revoke':
                    $pdo->prepare("UPDATE devices SET status = 'revoked' WHERE id = :id")->execute([':id' => (int)($_POST['id'] ?? 0)]);
                    $actionMsg = 'Device revoked — its token is rejected immediately.';
                    break;
                case 'reactivate':
                    $pdo->prepare("UPDATE devices SET status = 'active' WHERE id = :id")->execute([':id' => (int)($_POST['id'] ?? 0)]);
                    $actionMsg = 'Device reactivated.';
                    break;
                case 'delete_device':
                    $pdo->prepare('DELETE FROM devices WHERE id = :id')->execute([':id' => (int)($_POST['id'] ?? 0)]);
                    $actionMsg = 'Device deleted.';
                    break;
                case 'add_user':
                    if (!$canManageUsers) {
                        throw new RuntimeException('Only the owner can manage staff accounts.');
                    }
                    $email = strtolower(trim((string)($_POST['email'] ?? '')));
                    $name  = mb_substr(trim((string)($_POST['name'] ?? '')), 0, 100);
                    $role  = (string)($_POST['role'] ?? 'cashier');
                    $pass  = (string)($_POST['password'] ?? '');
                    if (!filter_var($email, FILTER_VALIDATE_EMAIL)) {
                        throw new RuntimeException('Enter a valid email address.');
                    }
                    if (!in_array($role, ['owner', 'manager', 'cashier'], true)) {
                        throw new RuntimeException('Unknown role.');
                    }
                    if (strlen($pass) < 8) {
                        throw new RuntimeException('Password must be at least 8 characters.');
                    }
                    try {
                        $pdo->prepare("INSERT INTO users (email, password_hash, name, role, status, created_ms)
                                       VALUES (:e, :p, :n, :r, 'active', :c)")
                            ->execute([':e' => $email, ':p' => password_hash($pass, PASSWORD_DEFAULT),
                                       ':n' => $name !== '' ? $name : null, ':r' => $role, ':c' => $now]);
                        $userCount++;
                        $actionMsg = "Account created: {$email} ({$role}). They can now sign in on a terminal to pair it (owner/manager) or on shift screens (cashier).";
                    } catch (Throwable $ex) {
                        if (stripos($ex->getMessage(), '1062') !== false || stripos($ex->getMessage(), 'Duplicate') !== false) {
                            throw new RuntimeException('That email already has an account.');
                        }
                        throw $ex;
                    }
                    break;
                case 'reset_password':
                    if (!$canManageUsers) {
                        throw new RuntimeException('Only the owner can reset passwords.');
                    }
                    $pass = (string)($_POST['password'] ?? '');
                    if (strlen($pass) < 8) {
                        throw new RuntimeException('Password must be at least 8 characters.');
                    }
                    $pdo->prepare('UPDATE users SET password_hash = :p WHERE id = :id')
                        ->execute([':p' => password_hash($pass, PASSWORD_DEFAULT), ':id' => (int)($_POST['id'] ?? 0)]);
                    $actionMsg = 'Password updated.';
                    break;
                case 'disable_user':
                    if (!$canManageUsers) {
                        throw new RuntimeException('Only the owner can manage staff accounts.');
                    }
                    $uid = (int)($_POST['id'] ?? 0);
                    if ($sessionUser && (int)$sessionUser['id'] === $uid) {
                        throw new RuntimeException('You cannot disable your own account here.');
                    }
                    $q = $pdo->prepare("SELECT role, status FROM users WHERE id = :id");
                    $q->execute([':id' => $uid]);
                    $t = $q->fetch();
                    if ($t && $t['role'] === 'owner' && $t['status'] === 'active') {
                        $st = $pdo->prepare("SELECT COUNT(*) FROM users WHERE role = 'owner' AND status = 'active' AND id <> :id");
                        $st->execute([':id' => $uid]);
                        $c = (int)$st->fetchColumn();
                        if ($c === 0) {
                            throw new RuntimeException('Cannot disable the last active owner account.');
                        }
                    }
                    $pdo->prepare("UPDATE users SET status = 'disabled' WHERE id = :id")->execute([':id' => $uid]);
                    $actionMsg = 'Account disabled — its sign-ins are rejected everywhere.';
                    break;
                case 'enable_user':
                    if (!$canManageUsers) {
                        throw new RuntimeException('Only the owner can manage staff accounts.');
                    }
                    $pdo->prepare("UPDATE users SET status = 'active' WHERE id = :id")->execute([':id' => (int)($_POST['id'] ?? 0)]);
                    $actionMsg = 'Account enabled.';
                    break;
                case 'toggle_require_device_auth':
                    if (!$canManageUsers) {
                        throw new RuntimeException('Only the owner can change the auth mode.');
                    }
                    $target = (string)($cfg['require_device_auth'] ?? '0') === '1' ? '0' : '1';
                    $out = $cfg;
                    $out['require_device_auth'] = $target;
                    file_put_contents($configPath,
                        "<?php\n// Managed by devices.php on " . gmdate('c') . " — NEVER commit this file.\n\nreturn " . var_export($out, true) . ";\n",
                        LOCK_EX);
                    @chmod($configPath, 0640);
                    if (function_exists('opcache_reset')) {
                        @opcache_reset();
                    }
                    $cfg = $out;
                    $actionMsg = $target === '1'
                        ? 'Device auth is now REQUIRED — the shared sync_token is rejected. Make sure every terminal has signed in first!'
                        : 'Legacy sync_token accepted again alongside device tokens.';
                    break;
                default:
                    $actionErr = 'Unknown action.';
            }
        } elseif ($action !== '' && $action !== 'login') {
            $actionErr = $admin ? 'Session expired — try again.' : 'Not signed in.';
        }
    } catch (Throwable $ex) {
        $actionErr = $ex->getMessage();
    }
}

$requireDevice = (string)($cfg['require_device_auth'] ?? '0') === '1';

// Re-evaluate admin state after login / logout / account creation in this request
$sessionUser = fetch_session_user($pdo);
$isOwner = $sessionUser !== null && $sessionUser['role'] === 'owner';
$isStaff = $sessionUser !== null && in_array($sessionUser['role'], ['owner', 'manager'], true);
$setupAdmin = !empty($_SESSION['setup_admin']);
$admin = $isStaff || $setupAdmin;
$canManageUsers = $isOwner || $setupAdmin;

try {
    $devices = $pdo->query(
        'SELECT d.id, d.name, d.branch_id, d.status, d.last_seen_ms, d.created_ms, u.email AS paired_as
         FROM devices d LEFT JOIN users u ON u.id = d.paired_by_user_id
         ORDER BY d.id DESC'
    )->fetchAll();
    $users = $pdo->query('SELECT id, email, name, role, status, created_ms FROM users ORDER BY id')->fetchAll();
    $tablesOk = true;
} catch (Throwable $ex) {
    $devices = [];
    $users = [];
    $tablesOk = false;
    $actionErr = 'Tables missing (' . $ex->getMessage() . ') — re-run install.php (steps 002/003).';
}

// Re-count users after create_owner inside the same request
if ($users !== []) {
    $userCount = count($users);
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex,nofollow">
<title>LoyPOS — devices &amp; staff</title>
<style>
 body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#0f172a;color:#e2e8f0;margin:0;padding:2rem;}
 .card{max-width:860px;margin:0 auto;background:#1e293b;border-radius:12px;padding:2rem;box-shadow:0 10px 30px rgba(0,0,0,.4);}
 h1{font-size:1.3rem;margin:0 0 .3rem;} .sub{color:#94a3b8;font-size:.9rem;margin-bottom:1.5rem;}
 h2{font-size:1.05rem;margin:1.8rem 0 .6rem;color:#cbd5e1;}
 table{width:100%;border-collapse:collapse;font-size:.88rem;}
 th{color:#94a3b8;text-align:left;padding:.5rem .6rem;border-bottom:1px solid #334155;}
 td{padding:.55rem .6rem;border-bottom:1px solid #1e293b;vertical-align:middle;}
 .badge{display:inline-block;padding:.15rem .55rem;border-radius:999px;font-size:.75rem;font-weight:600;}
 .ok{background:#14532d;color:#4ade80;} .warn{background:#713f12;color:#facc15;} .err{background:#7f1d1d;color:#fca5a5;}
 .mut{color:#94a3b8;font-size:.82rem;}
 form.inline{display:inline;}
 button{background:#334155;color:#e2e8f0;border:1px solid #475569;border-radius:8px;padding:.45rem .8rem;cursor:pointer;font-size:.85rem;}
 button.primary{background:#166534;border-color:#16a34a;color:#f0fdf4;}
 button.danger{background:#7f1d1d;border-color:#b91c1c;color:#fee2e2;}
 input[type=text],input[type=password],input[type=email],select{background:#0f172a;color:#e2e8f0;border:1px solid #475569;border-radius:8px;padding:.55rem .7rem;font-size:.9rem;width:100%;box-sizing:border-box;}
 label{display:block;margin:.8rem 0 .3rem;font-size:.9rem;color:#cbd5e1;}
 .msg{background:#14532d;color:#bbf7d0;border-radius:8px;padding:.7rem 1rem;margin:.8rem 0;font-size:.9rem;}
 .errbox{background:#7f1d1d;color:#fee2e2;border-radius:8px;padding:.7rem 1rem;margin:.8rem 0;font-size:.9rem;}
 .note{color:#94a3b8;font-size:.85rem;line-height:1.5;}
 .panel{background:#0f172a;border:1px solid #334155;border-radius:10px;padding:1rem 1.2rem;margin-top:1rem;}
 .grid{display:grid;grid-template-columns:1fr 1fr;gap:0 1rem;}
 .tblwrap{overflow-x:auto;}
 .topbar{display:flex;justify-content:space-between;align-items:center;gap:1rem;flex-wrap:wrap;}
 @media (max-width:640px){ body{padding:1rem} .card{padding:1.2rem} .grid{grid-template-columns:1fr} }
</style>
</head>
<body>
<div class="card">
 <div class="topbar">
  <div>
   <h1>LoyPOS — devices &amp; staff</h1>
   <div class="sub">Tier 2 per-device auth · terminals sign in with a staff account and register themselves</div>
  </div>
  <?php if ($sessionUser !== null): ?>
   <form method="post" class="inline">
    <input type="hidden" name="csrf" value="<?= e($_SESSION['csrf']) ?>">
    <input type="hidden" name="action" value="logout">
    <button type="submit">Sign out (<?= e($sessionUser['email']) ?>)</button>
   </form>
  <?php elseif ($setupAdmin): ?>
   <form method="post" class="inline">
    <input type="hidden" name="csrf" value="<?= e($_SESSION['csrf']) ?>">
    <input type="hidden" name="action" value="logout">
    <button type="submit" class="danger">End setup-token session</button>
   </form>
  <?php endif; ?>
 </div>

 <?php if ($actionMsg !== ''): ?><div class="msg"><?= e($actionMsg) ?></div><?php endif; ?>
 <?php if ($actionErr !== ''): ?><div class="errbox"><?= e($actionErr) ?></div><?php endif; ?>

<?php if (!$tablesOk || $usersMissing): ?>
 <div class="panel">
  <p style="margin:.2rem 0">The <code>users</code> table is missing — run
  <a style="color:#4ade80" href="install.php?token=<?= e($setupToken) ?>">install.php?token=<?= e($setupToken) ?></a>
  (it applies migration step <code>003_account_auth</code>), then reload this page.</p>
 </div>
<?php elseif (!$admin): ?>
 <h2>Sign in</h2>
 <form method="post" style="max-width:420px">
  <input type="hidden" name="csrf" value="<?= e($_SESSION['csrf']) ?>">
  <input type="hidden" name="action" value="login">
  <label>Email (owner or manager)</label>
  <input type="email" name="email" maxlength="190" required autofocus placeholder="you@yourshop.com">
  <label>Password</label>
  <input type="password" name="password" required>
  <div style="margin-top:1.2rem"><button type="submit" class="primary">Sign in</button></div>
 </form>
 <p class="note" style="margin-top:1rem">First time here, or lost the owner password?
 Open <code>devices.php?token=&lt;setup&nbsp;token&gt;</code> (the same link as the installer) to create the
 owner account or reset it.</p>
<?php elseif ($userCount === 0): ?>
 <div class="panel">
  <h2 style="margin-top:0">Create your owner account</h2>
  <p class="note">One-time setup. This account signs in on this page and on every POS terminal
  (app → Settings → Sign In &amp; Pair This Device). Use a strong password — it protects your shop data.</p>
  <form method="post" style="max-width:460px">
   <input type="hidden" name="csrf" value="<?= e($_SESSION['csrf']) ?>">
   <input type="hidden" name="action" value="create_owner">
   <label>Email</label>
   <input type="email" name="email" maxlength="190" required placeholder="you@yourshop.com">
   <label>Your name (optional)</label>
   <input type="text" name="name" maxlength="100" placeholder="Shop owner">
   <label>Password (min 8 characters)</label>
   <input type="password" name="password" minlength="8" required>
   <label>Repeat password</label>
   <input type="password" name="password2" minlength="8" required>
   <div style="margin-top:1.2rem"><button type="submit" class="primary">Create owner account</button></div>
  </form>
 </div>
<?php else: ?>
 <h2>Paired devices (<?= count($devices) ?>)</h2>
 <div class="tblwrap"><table>
  <tr><th>ID</th><th>Name</th><th>Status</th><th>Paired by</th><th>Paired</th><th>Last seen</th><th style="width:30%">Actions</th></tr>
  <?php if ($devices === []): ?>
   <tr><td colspan="7" class="mut">No terminals yet — in the app open Settings → Sign In &amp; Pair This Device
       and sign in with an owner or manager account. The terminal registers itself automatically.</td></tr>
  <?php endif; ?>
  <?php foreach ($devices as $dv): ?>
   <tr>
    <td><?= (int)$dv['id'] ?></td>
    <td><strong><?= e($dv['name']) ?></strong><?= $dv['branch_id'] ? '<br><span class="mut">branch ' . e($dv['branch_id']) . '</span>' : '' ?></td>
    <td><span class="badge <?= $dv['status'] === 'active' ? 'ok' : 'err' ?>"><?= e($dv['status']) ?></span></td>
    <td class="mut"><?= $dv['paired_as'] !== null ? e($dv['paired_as']) : '—' ?></td>
    <td class="mut"><?= e(ago((int)$dv['created_ms'])) ?></td>
    <td class="mut"><?= e(ago($dv['last_seen_ms'] !== null ? (int)$dv['last_seen_ms'] : null)) ?></td>
    <td>
     <?php if ($dv['status'] === 'active'): ?>
      <form method="post" class="inline" onsubmit="return confirm('Revoke this device? Its token stops working immediately.')">
       <input type="hidden" name="csrf" value="<?= e($_SESSION['csrf']) ?>"><input type="hidden" name="action" value="revoke"><input type="hidden" name="id" value="<?= (int)$dv['id'] ?>">
       <button type="submit" class="danger">Revoke</button>
      </form>
     <?php else: ?>
      <form method="post" class="inline">
       <input type="hidden" name="csrf" value="<?= e($_SESSION['csrf']) ?>"><input type="hidden" name="action" value="reactivate"><input type="hidden" name="id" value="<?= (int)$dv['id'] ?>">
       <button type="submit">Reactivate</button>
      </form>
     <?php endif; ?>
     <form method="post" class="inline" onsubmit="return confirm('Delete this device permanently?')">
      <input type="hidden" name="csrf" value="<?= e($_SESSION['csrf']) ?>"><input type="hidden" name="action" value="delete_device"><input type="hidden" name="id" value="<?= (int)$dv['id'] ?>">
      <button type="submit">Delete</button>
     </form>
    </td>
   </tr>
  <?php endforeach; ?>
 </table></div>

 <h2>Staff accounts (<?= count($users) ?>)</h2>
 <?php if ($canManageUsers): ?>
  <div class="panel">
   <form method="post">
    <input type="hidden" name="csrf" value="<?= e($_SESSION['csrf']) ?>">
    <input type="hidden" name="action" value="add_user">
    <div class="grid">
     <div><label>Email</label><input type="email" name="email" maxlength="190" required placeholder="cashier@yourshop.com"></div>
     <div><label>Name (optional)</label><input type="text" name="name" maxlength="100" placeholder="Daniel A."></div>
     <div><label>Role</label><select name="role">
       <option value="cashier">cashier — sell &amp; open shifts (cannot add terminals)</option>
       <option value="manager">manager — everything except staff management</option>
       <option value="owner">owner — full control</option>
      </select></div>
     <div><label>Password (min 8 characters)</label><input type="password" name="password" minlength="8" required></div>
    </div>
    <div style="margin-top:1rem"><button type="submit" class="primary">Add staff account</button></div>
   </form>
   <p class="note" style="margin-top:.6rem">Give each register its own account — that is how sales get attributed
   to the right cashier, and how you revoke a lost tablet without touching the others.</p>
  </div>
 <?php endif; ?>
 <div class="tblwrap"><table>
  <tr><th>ID</th><th>Email</th><th>Name</th><th>Role</th><th>Status</th><th>Created</th><th style="width:30%">Actions</th></tr>
  <?php if ($users === []): ?>
   <tr><td colspan="7" class="mut">No accounts.</td></tr>
  <?php endif; ?>
  <?php foreach ($users as $us): ?>
   <tr>
    <td><?= (int)$us['id'] ?></td>
    <td><strong><?= e($us['email']) ?></strong></td>
    <td><?= $us['name'] !== null ? e($us['name']) : '<span class="mut">—</span>' ?></td>
    <td><span class="badge <?= $us['role'] === 'owner' ? 'ok' : 'warn' ?>"><?= e($us['role']) ?></span></td>
    <td><span class="badge <?= $us['status'] === 'active' ? 'ok' : 'err' ?>"><?= e($us['status']) ?></span></td>
    <td class="mut"><?= e(ago((int)$us['created_ms'])) ?></td>
    <td>
     <?php if ($canManageUsers): ?>
      <form method="post" class="inline" onsubmit="return confirm('Set a new password for this account?')">
       <input type="hidden" name="csrf" value="<?= e($_SESSION['csrf']) ?>"><input type="hidden" name="action" value="reset_password"><input type="hidden" name="id" value="<?= (int)$us['id'] ?>">
       <input type="password" name="password" minlength="8" required placeholder="new password" style="width:150px">
       <button type="submit">Reset password</button>
      </form>
      <?php if ($us['status'] === 'active'): ?>
       <form method="post" class="inline" onsubmit="return confirm('Disable this account? Its sign-ins stop working everywhere.')">
        <input type="hidden" name="csrf" value="<?= e($_SESSION['csrf']) ?>"><input type="hidden" name="action" value="disable_user"><input type="hidden" name="id" value="<?= (int)$us['id'] ?>">
        <button type="submit" class="danger">Disable</button>
       </form>
      <?php else: ?>
       <form method="post" class="inline">
        <input type="hidden" name="csrf" value="<?= e($_SESSION['csrf']) ?>"><input type="hidden" name="action" value="enable_user"><input type="hidden" name="id" value="<?= (int)$us['id'] ?>">
        <button type="submit">Enable</button>
       </form>
      <?php endif; ?>
     <?php else: ?>
      <span class="mut">owner only</span>
     <?php endif; ?>
    </td>
   </tr>
  <?php endforeach; ?>
 </table></div>

 <h2>Auth mode</h2>
 <div class="panel">
  <p style="margin:.2rem 0 .8rem">
   Current mode:
   <?php if ($requireDevice): ?>
    <span class="badge err">device tokens only</span>
    <span class="note">— the shared sync_token is <strong>rejected</strong>. Terminals that never signed in (including old APK builds) cannot sync until they do.</span>
   <?php else: ?>
    <span class="badge warn">transition — legacy token accepted</span>
    <span class="note">— both the shared sync_token and per-device tokens work. Recommended while you sign in on each terminal; switch to device-only once every tablet is paired.</span>
   <?php endif; ?>
  </p>
  <?php if ($canManageUsers): ?>
   <form method="post" onsubmit="return confirm('Change authentication mode now?')">
    <input type="hidden" name="csrf" value="<?= e($_SESSION['csrf']) ?>">
    <input type="hidden" name="action" value="toggle_require_device_auth">
    <button type="submit" class="<?= $requireDevice ? 'primary' : 'danger' ?>">
     <?= $requireDevice ? 'Allow legacy sync_token again (transition mode)' : 'Require device tokens (disable shared sync_token)' ?>
    </button>
   </form>
  <?php endif; ?>
  <p class="note" style="margin-top:.8rem">Writes <code>require_device_auth =&gt; '1'/'0'</code> into <code>config.php</code>.
  Device tokens never expire but can be revoked in one click above — that is the point of Tier 2.</p>
 </div>
<?php endif; ?>
</div>
</body>
</html>
