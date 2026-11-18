<?php
declare(strict_types=1);
/**
 * LoyPOS — device pairing admin (Tier 2 per-device auth)
 *
 *   https://novelsapps.com.et/api/v1/devices.php?token=<SETUP_TOKEN>
 *
 * Generate one-time pairing codes (valid 15 min, shown once), review paired
 * devices, revoke/reactivate/delete them, and switch "require device auth"
 * on/off. Uses the same SETUP_TOKEN as install.php. This page is the ongoing
 * device-management console — keep it deployed; add Plesk IP restrictions if
 * you want extra protection.
 *
 * Idempotent and read-only by default: nothing changes unless you click an action.
 */

const SETUP_TOKEN  = 'mequbbzjki';
const PAIR_TTL_MS  = 15 * 60 * 1000; // pairing codes live 15 minutes
// Unambiguous alphabet — codes never contain 0/O/1/I so they survive handwriting
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

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

function until(int $ms): string {
    $d = $ms - (int)round(microtime(true) * 1000);
    if ($d <= 0) {
        return 'expired';
    }
    foreach ([['h', 3600000], ['m', 60000]] as [$u, $div]) {
        if ($d >= $div) {
            return intdiv($d, $div) . $u . ' left';
        }
    }
    return max(1, (int)ceil($d / 1000)) . 's left';
}

$token = (string)($_REQUEST['token'] ?? '');
if ($token === '' || !hash_equals(SETUP_TOKEN, $token)) {
    http_response_code(401);
    header('Content-Type: text/html; charset=utf-8');
    echo '<!DOCTYPE html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">'
        . '<meta name="robots" content="noindex,nofollow"><title>LoyPOS devices</title>'
        . '<style>body{font-family:system-ui,sans-serif;background:#0f172a;color:#e2e8f0;display:flex;min-height:100vh;align-items:center;justify-content:center;margin:0}'
        . '.g{background:#1e293b;padding:2rem;border-radius:12px;max-width:420px;margin:1rem}</style></head>'
        . '<body><div class="g"><h1>LoyPOS device admin</h1><p>Forbidden — open this page with the setup token:</p>'
        . '<code>devices.php?token=&lt;your setup token&gt;</code>'
        . '<p style="color:#94a3b8;font-size:.85rem">The token is the same one used for install.php (SETUP_TOKEN in that file).</p></div></body></html>';
    exit;
}

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
        . e($dbError) . '</p><p>Run <a style="color:#4ade80" href="install.php?token=' . e(SETUP_TOKEN) . '">install.php</a> first — '
        . 'it creates the devices and pairing_codes tables (migration step 002_device_auth).</p></div></body></html>';
    exit;
}

$now = (int)round(microtime(true) * 1000);
$newCode = '';       // shown once, only in the response that created it
$actionMsg = '';
$actionErr = '';

if ($_SERVER['REQUEST_METHOD'] === 'POST' && hash_equals(SETUP_TOKEN, (string)($_POST['token'] ?? ''))) {
    $action = (string)($_POST['action'] ?? '');
    try {
        switch ($action) {
            case 'new_code':
                $hint = mb_substr(trim((string)($_POST['name_hint'] ?? '')), 0, 100);
                $code = '';
                for ($i = 0; $i < 8; $i++) {
                    $code .= CODE_ALPHABET[random_int(0, strlen(CODE_ALPHABET) - 1)];
                }
                $pdo->prepare('INSERT INTO pairing_codes (code_hash, name_hint, expires_ms, used_ms, created_ms) VALUES (:h, :n, :e, NULL, :c)')
                    ->execute([':h' => hash('sha256', $code), ':n' => $hint !== '' ? $hint : null,
                               ':e' => $now + PAIR_TTL_MS, ':c' => $now]);
                $newCode = $code;
                $actionMsg = 'Pairing code generated — valid 15 minutes, shown once.';
                break;
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
            case 'delete_code':
                $pdo->prepare('DELETE FROM pairing_codes WHERE id = :id AND used_ms IS NULL')->execute([':id' => (int)($_POST['id'] ?? 0)]);
                $actionMsg = 'Unused pairing code deleted.';
                break;
            case 'toggle_require_device_auth':
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
                    ? 'Device auth is now REQUIRED — the shared sync_token is rejected. Make sure every terminal has paired first!'
                    : 'Legacy sync_token accepted again alongside device tokens.';
                break;
        }
    } catch (Throwable $ex) {
        $actionErr = $ex->getMessage();
    }
}

$requireDevice = (string)($cfg['require_device_auth'] ?? '0') === '1';

try {
    $devices = $pdo->query('SELECT id, name, branch_id, status, last_seen_ms, created_ms FROM devices ORDER BY id DESC')->fetchAll();
    $codes = $pdo->query('SELECT id, name_hint, expires_ms, used_ms, created_ms FROM pairing_codes ORDER BY id DESC LIMIT 20')->fetchAll();
    $tablesOk = true;
} catch (Throwable $ex) {
    $devices = [];
    $codes = [];
    $tablesOk = false;
    $actionErr = 'Tables missing (' . $ex->getMessage() . ') — re-run install.php (step 002_device_auth).';
}
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex,nofollow">
<title>LoyPOS — devices &amp; pairing</title>
<style>
 body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:#0f172a;color:#e2e8f0;margin:0;padding:2rem;}
 .card{max-width:820px;margin:0 auto;background:#1e293b;border-radius:12px;padding:2rem;box-shadow:0 10px 30px rgba(0,0,0,.4);}
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
 input[type=text]{background:#0f172a;color:#e2e8f0;border:1px solid #475569;border-radius:8px;padding:.55rem .7rem;font-size:.9rem;width:100%;box-sizing:border-box;}
 label{display:block;margin:.8rem 0 .3rem;font-size:.9rem;color:#cbd5e1;}
 .bigcode{font-family:ui-monospace,SFMono-Regular,monospace;font-size:2rem;letter-spacing:.35em;background:#0f172a;border:1px dashed #475569;
          border-radius:10px;padding:1rem;text-align:center;color:#4ade80;margin:.8rem 0;}
 .msg{background:#14532d;color:#bbf7d0;border-radius:8px;padding:.7rem 1rem;margin:.8rem 0;font-size:.9rem;}
 .errbox{background:#7f1d1d;color:#fee2e2;border-radius:8px;padding:.7rem 1rem;margin:.8rem 0;font-size:.9rem;}
 .note{color:#94a3b8;font-size:.85rem;line-height:1.5;}
 .panel{background:#0f172a;border:1px solid #334155;border-radius:10px;padding:1rem 1.2rem;margin-top:1rem;}
 .tblwrap{overflow-x:auto;}
 @media (max-width:640px){ body{padding:1rem} .card{padding:1.2rem} }
</style>
</head>
<body>
<div class="card">
 <h1>LoyPOS — devices &amp; pairing</h1>
 <div class="sub">Tier 2 per-device auth · each terminal gets its own token, revocable independently</div>

 <?php if ($actionMsg !== ''): ?><div class="msg"><?= e($actionMsg) ?></div><?php endif; ?>
 <?php if ($actionErr !== ''): ?><div class="errbox"><?= e($actionErr) ?></div><?php endif; ?>

 <?php if ($newCode !== ''): ?>
  <div class="panel">
   <strong style="color:#4ade80">New pairing code<?= $hint !== '' ? ' for ' . e($hint) : '' ?>:</strong>
   <div class="bigcode"><?= e($newCode) ?></div>
   <p class="note">Shown <strong>once</strong> — copy it now and enter it in the app (Settings → Pair This Device)
   within <strong>15 minutes</strong>. Only a hash is stored; if lost, generate a new one.</p>
  </div>
 <?php endif; ?>

 <h2>Generate pairing code</h2>
 <form method="post" class="tblwrap">
  <input type="hidden" name="token" value="<?= e(SETUP_TOKEN) ?>">
  <input type="hidden" name="action" value="new_code">
  <label>Device name hint (optional — the app can override it, e.g. &quot;Register 1&quot;, &quot;Bar tablet&quot;)</label>
  <input type="text" name="name_hint" maxlength="100" placeholder="Register 1">
  <div style="margin-top:1rem"><button type="submit" class="primary">Generate one-time code (15 min)</button></div>
 </form>

 <h2>Paired devices (<?= count($devices) ?>)</h2>
 <div class="tblwrap"><table>
  <tr><th>ID</th><th>Name</th><th>Status</th><th>Paired</th><th>Last seen</th><th style="width:40%">Actions</th></tr>
  <?php if ($devices === []): ?>
   <tr><td colspan="6" class="mut">No devices paired yet — generate a code above and pair the terminal in the app.</td></tr>
  <?php endif; ?>
  <?php foreach ($devices as $dv): ?>
   <tr>
    <td><?= (int)$dv['id'] ?></td>
    <td><strong><?= e($dv['name']) ?></strong><?= $dv['branch_id'] ? '<br><span class="mut">branch ' . e($dv['branch_id']) . '</span>' : '' ?></td>
    <td><span class="badge <?= $dv['status'] === 'active' ? 'ok' : 'err' ?>"><?= e($dv['status']) ?></span></td>
    <td class="mut"><?= e(ago((int)$dv['created_ms'])) ?></td>
    <td class="mut"><?= e(ago($dv['last_seen_ms'] !== null ? (int)$dv['last_seen_ms'] : null)) ?></td>
    <td>
     <?php if ($dv['status'] === 'active'): ?>
      <form method="post" class="inline" onsubmit="return confirm('Revoke this device? Its token stops working immediately.')">
       <input type="hidden" name="token" value="<?= e(SETUP_TOKEN) ?>"><input type="hidden" name="action" value="revoke"><input type="hidden" name="id" value="<?= (int)$dv['id'] ?>">
       <button type="submit" class="danger">Revoke</button>
      </form>
     <?php else: ?>
      <form method="post" class="inline">
       <input type="hidden" name="token" value="<?= e(SETUP_TOKEN) ?>"><input type="hidden" name="action" value="reactivate"><input type="hidden" name="id" value="<?= (int)$dv['id'] ?>">
       <button type="submit">Reactivate</button>
      </form>
     <?php endif; ?>
     <form method="post" class="inline" onsubmit="return confirm('Delete this device permanently?')">
      <input type="hidden" name="token" value="<?= e(SETUP_TOKEN) ?>"><input type="hidden" name="action" value="delete_device"><input type="hidden" name="id" value="<?= (int)$dv['id'] ?>">
      <button type="submit">Delete</button>
     </form>
    </td>
   </tr>
  <?php endforeach; ?>
 </table></div>

 <h2>Recent pairing codes (last 20)</h2>
 <div class="tblwrap"><table>
  <tr><th>ID</th><th>For</th><th>Status</th><th>Created</th><th style="width:25%">Actions</th></tr>
  <?php if ($codes === []): ?>
   <tr><td colspan="5" class="mut">No pairing codes yet.</td></tr>
  <?php endif; ?>
  <?php foreach ($codes as $cd): ?>
   <?php
        $status = $cd['used_ms'] !== null ? '<span class="badge warn">used</span>'
            : ((int)$cd['expires_ms'] < $now ? '<span class="badge err">expired</span>'
            : '<span class="badge ok">' . e(until((int)$cd['expires_ms'])) . '</span>');
   ?>
   <tr>
    <td><?= (int)$cd['id'] ?></td>
    <td><?= $cd['name_hint'] !== null ? e($cd['name_hint']) : '<span class="mut">—</span>' ?></td>
    <td><?= $status ?></td>
    <td class="mut"><?= e(ago((int)$cd['created_ms'])) ?></td>
    <td>
     <?php if ($cd['used_ms'] === null): ?>
      <form method="post" class="inline">
       <input type="hidden" name="token" value="<?= e(SETUP_TOKEN) ?>"><input type="hidden" name="action" value="delete_code"><input type="hidden" name="id" value="<?= (int)$cd['id'] ?>">
       <button type="submit">Delete</button>
      </form>
     <?php else: ?>
      <span class="mut">—</span>
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
    <span class="note">— the shared sync_token is <strong>rejected</strong>. Unpaired terminals (including old APK builds) cannot sync until paired.</span>
   <?php else: ?>
    <span class="badge warn">transition — legacy token accepted</span>
    <span class="note">— both the shared sync_token and per-device tokens work. Recommended while you pair each terminal; switch to device-only once every tablet is paired.</span>
   <?php endif; ?>
  </p>
  <form method="post" onsubmit="return confirm('Change authentication mode now?')">
   <input type="hidden" name="token" value="<?= e(SETUP_TOKEN) ?>">
   <input type="hidden" name="action" value="toggle_require_device_auth">
   <button type="submit" class="<?= $requireDevice ? 'primary' : 'danger' ?>">
    <?= $requireDevice ? 'Allow legacy sync_token again (transition mode)' : 'Require device tokens (disable shared sync_token)' ?>
   </button>
  </form>
  <p class="note" style="margin-top:.8rem">Writes <code>require_device_auth =&gt; '1'/'0'</code> into <code>config.php</code>.
  Device tokens never expire but can be revoked in one click above — that is the point of Tier 2.</p>
 </div>
</div>
</body>
</html>
