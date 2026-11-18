<?php
declare(strict_types=1);
/**
 * LoyPOS — Admin Dashboard
 *
 *   https://novelsapps.com.et/api/v1/admin.php
 *
 * A single, professional overview of the whole shop, protected by the SAME
 * account sign-in as devices.php (owner / manager, PHP sessions + CSRF).
 * One login signs you into every admin page.
 *
 * Sections:
 *   - headline metrics (revenue today / 7-day / all-time, sales, avg basket)
 *   - 14-day revenue chart (pure CSS, no JS libraries)
 *   - recent sales, top products, low stock
 *   - staff accounts + salary payments (recorded in the app's Back Office)
 *
 * Read-only: this page never mutates shop data. Account & device management
 * lives in devices.php.
 */

// ── Session (hardened cookie — same as devices.php) ─────────────────────────
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
        . '<meta name="robots" content="noindex,nofollow"><title>LoyPOS admin</title>'
        . '<style>body{font-family:system-ui,sans-serif;background:#0b1220;color:#e2e8f0;margin:0;padding:2rem}'
        . '.card{max-width:680px;margin:2rem auto;background:#111a2e;border-radius:14px;padding:2rem}</style></head>'
        . '<body><div class="card"><h1>LoyPOS admin dashboard</h1><p style="color:#f87171">Database unavailable: '
        . e($dbError) . '</p><p>Run install.php first (setup token required).</p></div></body></html>';
    exit;
}

// Setup token grant — the master key for bootstrap & recovery, same contract
// as devices.php: use it ONCE in the browser and this session gets admin
// rights. Forms only carry the CSRF token afterwards, never the secret.
$setupToken = trim((string)($cfg['setup_token'] ?? ''));
$setupTokenInput = (string)($_REQUEST['token'] ?? '');
if ($setupToken !== '' && $setupTokenInput !== '' && hash_equals($setupToken, $setupTokenInput)
    && empty($_SESSION['setup_admin'])) {
    session_regenerate_id(true);
    $_SESSION['setup_admin'] = true;
}

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
        // users table missing — fall through to sign-out
    }
    unset($_SESSION['devadmin_uid']);
    return null;
}

// ── POST actions: login / logout only (the dashboard itself is read-only) ───
$now = (int)round(microtime(true) * 1000);
$actionMsg = '';
$actionErr = '';
if (empty($_SESSION['csrf'])) {
    $_SESSION['csrf'] = bin2hex(random_bytes(16));
}
$csrfOk = isset($_POST['csrf']) && hash_equals($_SESSION['csrf'], (string)$_POST['csrf']);

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    $action = (string)($_POST['action'] ?? '');
    try {
        if ($action === 'login') {
            if (too_many_attempts('adminlogin')) {
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
                usleep(400000);
                throw new RuntimeException('Invalid email or password (only owner/manager accounts can sign in).');
            }
            session_regenerate_id(true);
            $_SESSION['devadmin_uid'] = (int)$u['id'];
        } elseif ($action === 'logout') {
            $_SESSION = [];
            session_destroy();
            session_start();
            $_SESSION['csrf'] = bin2hex(random_bytes(16));
            $actionMsg = 'Signed out.';
        } elseif ($action !== '') {
            $actionErr = 'Unknown action.';
        }
    } catch (Throwable $ex) {
        $actionErr = $ex->getMessage();
    }
}

$sessionUser = fetch_session_user($pdo);
$setupAdmin = !empty($_SESSION['setup_admin']);
$isAdmin = $sessionUser !== null || $setupAdmin;

// ── Shop data (every query degrades gracefully if a table is missing) ───────
function q(PDO $pdo, string $sql, array $params = [], $default = null) {
    try {
        $st = $pdo->prepare($sql);
        $st->execute($params);
        return $st->fetchAll();
    } catch (Throwable $ex) {
        return $default ?? [];
    }
}
function q1(PDO $pdo, string $sql, array $params = [], $default = 0) {
    try {
        $st = $pdo->prepare($sql);
        $st->execute($params);
        $v = $st->fetchColumn();
        return $v === false ? $default : $v;
    } catch (Throwable $ex) {
        return $default;
    }
}

$storeName = (string) q1($pdo, "SELECT setting_value FROM shop_settings WHERE setting_key = 'store_name' LIMIT 1", [], 'My Shop');
$currency  = (string) q1($pdo, "SELECT setting_value FROM shop_settings WHERE setting_key = 'currency_symbol' LIMIT 1", [], 'ETB');
$lowThreshold = (double) q1($pdo, "SELECT setting_value FROM shop_settings WHERE setting_key = 'low_stock_threshold' LIMIT 1", [], 5.0);

function money(float|int $cents, string $symbol): string {
    return $symbol . ' ' . number_format(((float)$cents) / 100.0, 2);
}

$startOfToday = strtotime('today') * 1000;
$ms7 = $startOfToday - 6 * 86400000;
$msMonth = strtotime(date('Y-m-01')) * 1000;

$revToday   = (int) q1($pdo, "SELECT COALESCE(SUM(total_cents),0) FROM sales WHERE status='completed' AND created_at >= :s", [':s' => $startOfToday]);
$rev7       = (int) q1($pdo, "SELECT COALESCE(SUM(total_cents),0) FROM sales WHERE status='completed' AND created_at >= :s", [':s' => $ms7]);
$revAll     = (int) q1($pdo, "SELECT COALESCE(SUM(total_cents),0) FROM sales WHERE status='completed'");
$revMonth   = (int) q1($pdo, "SELECT COALESCE(SUM(total_cents),0) FROM sales WHERE status='completed' AND created_at >= :s", [':s' => $msMonth]);
$salesToday = (int) q1($pdo, "SELECT COUNT(*) FROM sales WHERE status='completed' AND created_at >= :s", [':s' => $startOfToday]);
$salesAll   = (int) q1($pdo, "SELECT COUNT(*) FROM sales WHERE status='completed'");
$avgBasket  = $salesAll > 0 ? (int)round($revAll / $salesAll) : 0;

$itemsCount    = (int) q1($pdo, 'SELECT COUNT(*) FROM products WHERE archived = 0');
$customersN    = (int) q1($pdo, 'SELECT COUNT(*) FROM customers');
$staffActive   = (int) q1($pdo, "SELECT COUNT(*) FROM users WHERE status = 'active'");
$devicesActive = (int) q1($pdo, "SELECT COUNT(*) FROM devices WHERE status = 'active'");
$branchesN     = (int) q1($pdo, 'SELECT COUNT(*) FROM branches WHERE archived = 0');

$lowStock = q($pdo,
    'SELECT name, stock, price_cents FROM products
     WHERE track_stock = 1 AND archived = 0 AND stock <= :th
     ORDER BY stock ASC LIMIT 10', [':th' => $lowThreshold]);
$lowStockCount = (int) q1($pdo,
    'SELECT COUNT(*) FROM products WHERE track_stock = 1 AND archived = 0 AND stock <= :th', [':th' => $lowThreshold]);

$recentSales = q($pdo,
    'SELECT number, created_at, cashier_name, customer_name, total_cents, payment_type, status
     FROM sales ORDER BY created_at DESC LIMIT 12');

$topProducts = q($pdo,
    "SELECT si.name, SUM(si.qty) AS qty, SUM(si.line_total_cents) AS total
     FROM sale_items si JOIN sales s ON s.id = si.receipt_id
     WHERE s.status = 'completed'
     GROUP BY si.name ORDER BY qty DESC LIMIT 8");

// Revenue for the last 14 days — per-day sums (ms epoch), rendered as CSS bars.
$chart = [];
$maxDay = 0.0;
for ($i = 13; $i >= 0; $i--) {
    $dayStart = $startOfToday - $i * 86400000;
    $dayEnd = $dayStart + 86400000;
    $t = (float) q1($pdo,
        "SELECT COALESCE(SUM(total_cents),0) FROM sales
         WHERE status='completed' AND created_at >= :a AND created_at < :b",
        [':a' => $dayStart, ':b' => $dayEnd]);
    $label = date('D j', (int)($dayStart / 1000));
    $chart[] = ['label' => $label, 'total' => $t];
    if ($t > $maxDay) {
        $maxDay = $t;
    }
}

$users = q($pdo, 'SELECT id, email, name, role, status, created_ms FROM users ORDER BY role DESC, id ASC');

$salaryByStaff = q($pdo,
    "SELECT COALESCE(NULLIF(staff_name,''), staff_email) AS label,
            staff_email, SUM(amount_cents) AS total, COUNT(*) AS n, MAX(paid_at) AS last_paid
     FROM salary_payments GROUP BY staff_email ORDER BY total DESC LIMIT 12");
$salaryTotal    = (int) q1($pdo, 'SELECT COALESCE(SUM(amount_cents),0) FROM salary_payments');
$salaryMonth    = (int) q1($pdo, 'SELECT COALESCE(SUM(amount_cents),0) FROM salary_payments WHERE paid_at >= :s', [':s' => $msMonth]);
$salaryCount    = (int) q1($pdo, 'SELECT COUNT(*) FROM salary_payments');
$salaryRecent   = q($pdo,
    'SELECT staff_name, staff_email, amount_cents, currency_symbol, note, paid_by_name, paid_at
     FROM salary_payments ORDER BY paid_at DESC LIMIT 12');
?>
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="robots" content="noindex,nofollow">
<title><?= e($storeName) ?> — LoyPOS Admin</title>
<style>
 :root{
   --bg:#0b1220; --panel:#111a2e; --panel2:#0e1626; --line:#1f2b44;
   --text:#e6edf7; --mut:#8ea0bd; --green:#22c55e; --green-soft:#0f2e1c;
   --teal:#2dd4bf; --amber:#f59e0b; --red:#ef4444;
 }
 *{box-sizing:border-box}
 body{font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:var(--bg);color:var(--text);margin:0;}
 a{color:var(--teal);text-decoration:none}
 .wrap{max-width:1120px;margin:0 auto;padding:0 16px 48px}
 header.top{background:linear-gradient(135deg,#0d1b33,#12305a);border-bottom:1px solid var(--line);}
 .topbar{max-width:1120px;margin:0 auto;padding:18px 16px;display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap}
 .brand{display:flex;align-items:center;gap:12px}
 .logo{width:42px;height:42px;border-radius:12px;background:var(--green);display:flex;align-items:center;justify-content:center;font-weight:800;color:#052e12;font-size:1.05rem}
 h1{font-size:1.15rem;margin:0} .sub{color:#b8c6de;font-size:.85rem}
 .navbtns{display:flex;gap:8px;flex-wrap:wrap}
 .btn{background:#1b2947;color:var(--text);border:1px solid #2c3d63;border-radius:9px;padding:.5rem .85rem;cursor:pointer;font-size:.85rem}
 .btn:hover{background:#22335a}
 .btn.primary{background:var(--green);border-color:var(--green);color:#04250f;font-weight:700}
 .btn.danger{background:#3b1420;border-color:#6b2030;color:#fecdd3}
 h2{font-size:1rem;margin:2rem 0 .8rem;color:#c9d6ea}
 .grid{display:grid;grid-template-columns:repeat(4,1fr);gap:12px}
 .grid.r2{grid-template-columns:repeat(5,1fr)}
 .card{background:var(--panel);border:1px solid var(--line);border-radius:14px;padding:16px}
 .card .k{color:var(--mut);font-size:.78rem;text-transform:uppercase;letter-spacing:.04em}
 .card .v{font-size:1.45rem;font-weight:750;margin-top:6px}
 .card .d{color:var(--mut);font-size:.78rem;margin-top:4px}
 .card.accent{border-color:#1d4d2f;background:linear-gradient(180deg,#12251a,#111a2e)}
 .two{display:grid;grid-template-columns:1fr 1fr;gap:12px}
 table{width:100%;border-collapse:collapse;font-size:.88rem}
 th{color:var(--mut);text-align:left;padding:.55rem .6rem;border-bottom:1px solid var(--line);font-weight:600;font-size:.78rem;text-transform:uppercase;letter-spacing:.04em}
 td{padding:.6rem .6rem;border-bottom:1px solid #16213a;vertical-align:middle}
 tr:last-child td{border-bottom:none}
 .tblwrap{background:var(--panel);border:1px solid var(--line);border-radius:14px;padding:6px 12px;overflow-x:auto}
 .badge{display:inline-block;padding:.14rem .55rem;border-radius:999px;font-size:.74rem;font-weight:600}
 .b-ok{background:#0f2e1c;color:#4ade80}.b-warn{background:#3a2a08;color:#fbbf24}
 .b-err{background:#3b1420;color:#f87171}.b-info{background:#0c2a3f;color:#38bdf8}
 .mut{color:var(--mut)}
 .chart{display:flex;align-items:flex-end;gap:6px;height:180px;background:var(--panel);border:1px solid var(--line);border-radius:14px;padding:16px}
 .bar{flex:1;background:linear-gradient(180deg,#22c55e,#15803d);border-radius:6px 6px 2px 2px;min-height:3px;position:relative}
 .bar span{position:absolute;top:-20px;left:50%;transform:translateX(-50%);font-size:.66rem;color:var(--mut);white-space:nowrap}
 .bar b{position:absolute;bottom:-20px;left:50%;transform:translateX(-50%);font-size:.66rem;color:var(--mut);font-weight:500;white-space:nowrap}
 .login{max-width:420px;margin:7vh auto;background:var(--panel);border:1px solid var(--line);border-radius:18px;padding:2rem}
 .login h1{font-size:1.3rem;margin:0 0 .2rem}
 input[type=email],input[type=password],input[type=text]{background:#0a1120;color:var(--text);border:1px solid #2c3d63;border-radius:10px;padding:.65rem .8rem;font-size:.95rem;width:100%}
 label{display:block;margin:.9rem 0 .3rem;font-size:.85rem;color:#c9d6ea}
 .msg{background:#0f2e1c;color:#bbf7d0;border-radius:10px;padding:.7rem 1rem;margin:1rem 0;font-size:.9rem}
 .errbox{background:#3b1420;color:#fecdd3;border-radius:10px;padding:.7rem 1rem;margin:1rem 0;font-size:.9rem}
 .foot{color:var(--mut);font-size:.8rem;margin-top:3rem;display:flex;gap:1rem;flex-wrap:wrap;justify-content:space-between}
 @media (max-width:900px){.grid{grid-template-columns:repeat(2,1fr)}.grid.r2{grid-template-columns:repeat(2,1fr)}.two{grid-template-columns:1fr}}
 @media (max-width:520px){.grid,.grid.r2{grid-template-columns:1fr}.card .v{font-size:1.25rem}}
</style>
</head>
<body>
<header class="top"><div class="topbar">
 <div class="brand">
  <div class="logo">L</div>
  <div>
   <h1><?= e($storeName) ?> — Admin Dashboard</h1>
   <div class="sub">LoyPOS · live overview from the synced MySQL database</div>
  </div>
 </div>
 <div class="navbtns">
  <?php if ($sessionUser !== null || $setupAdmin): ?>
   <a class="btn" href="devices.php">Devices &amp; staff accounts</a>
   <form method="post" style="display:inline">
    <input type="hidden" name="csrf" value="<?= e($_SESSION['csrf']) ?>">
    <input type="hidden" name="action" value="logout">
    <button class="btn danger" type="submit">Sign out<?= $sessionUser !== null ? ' (' . e($sessionUser['email']) . ')' : ' (setup session)' ?></button>
   </form>
  <?php endif; ?>
 </div>
</div></header>

<div class="wrap">
<?php if ($actionMsg !== ''): ?><div class="msg"><?= e($actionMsg) ?></div><?php endif; ?>
<?php if ($actionErr !== ''): ?><div class="errbox"><?= e($actionErr) ?></div><?php endif; ?>

<?php if (!$isAdmin): ?>
 <div class="login">
  <h1>Sign in</h1>
  <p class="sub" style="color:var(--mut);font-size:.88rem">Owner or manager account · same credentials as devices.php and the POS terminals.</p>
  <?php if ($actionErr !== ''): ?><div class="errbox"><?= e($actionErr) ?></div><?php endif; ?>
  <form method="post">
   <input type="hidden" name="csrf" value="<?= e($_SESSION['csrf']) ?>">
   <input type="hidden" name="action" value="login">
   <label>Email</label>
   <input type="email" name="email" maxlength="190" required autofocus placeholder="you@yourshop.com">
   <label>Password</label>
   <input type="password" name="password" required>
   <div style="margin-top:1.3rem"><button class="btn primary" type="submit" style="width:100%;padding:.7rem">Sign in to dashboard</button></div>
  </form>
  <p class="mut" style="font-size:.82rem;margin-top:1.1rem">First time, or lost the owner password?
  Open <code>devices.php?token=&lt;setup&nbsp;token&gt;</code> to bootstrap or recover.</p>
 </div>

<?php else: ?>

 <h2>Revenue</h2>
 <div class="grid">
  <div class="card accent"><div class="k">Today</div><div class="v"><?= e(money($revToday, $currency)) ?></div><div class="d"><?= (int)$salesToday ?> sale<?= $salesToday === 1 ? '' : 's' ?> today</div></div>
  <div class="card"><div class="k">Last 7 days</div><div class="v"><?= e(money($rev7, $currency)) ?></div><div class="d">rolling week</div></div>
  <div class="card"><div class="k">This month</div><div class="v"><?= e(money($revMonth, $currency)) ?></div><div class="d"><?= date('F Y') ?></div></div>
  <div class="card"><div class="k">All time</div><div class="v"><?= e(money($revAll, $currency)) ?></div><div class="d"><?= (int)$salesAll ?> sales · avg <?= e(money($avgBasket, $currency)) ?></div></div>
 </div>

 <h2>Last 14 days</h2>
 <div class="chart">
  <?php foreach ($chart as $c): $h = $maxDay > 0 ? max(3, (int)round($c['total'] / $maxDay * 140)) : 3; ?>
   <div class="bar" style="height:<?= $h ?>px">
    <?php if ($c['total'] > 0): ?><span><?= e(number_format($c['total'] / 100.0, 0)) ?></span><?php endif; ?>
    <b><?= e($c['label']) ?></b>
   </div>
  <?php endforeach; ?>
 </div>

 <h2>Shop at a glance</h2>
 <div class="grid r2">
  <div class="card"><div class="k">Products</div><div class="v"><?= $itemsCount ?></div><div class="d">active in catalog</div></div>
  <div class="card"><div class="k">Low stock</div><div class="v" style="color:<?= $lowStockCount > 0 ? 'var(--amber)' : 'var(--green)' ?>"><?= $lowStockCount ?></div><div class="d">≤ <?= e((string)$lowThreshold) ?> units left</div></div>
  <div class="card"><div class="k">Staff</div><div class="v"><?= $staffActive ?></div><div class="d">active accounts</div></div>
  <div class="card"><div class="k">Terminals</div><div class="v"><?= $devicesActive ?></div><div class="d">paired devices</div></div>
  <div class="card"><div class="k">Customers</div><div class="v"><?= $customersN ?></div><div class="d"><?= $branchesN ?> branch<?= $branchesN === 1 ? '' : 'es' ?></div></div>
 </div>

 <div class="two" style="margin-top:1.6rem">
  <div>
   <h2>Recent sales</h2>
   <div class="tblwrap"><table>
    <tr><th>#</th><th>When</th><th>Cashier</th><th>Total</th><th>Status</th></tr>
    <?php if ($recentSales === []): ?><tr><td colspan="5" class="mut">No sales synced yet.</td></tr><?php endif; ?>
    <?php foreach ($recentSales as $s): ?>
     <tr>
      <td><strong>#<?= (int)$s['number'] ?></strong></td>
      <td class="mut"><?= e(ago((int)$s['created_at'])) ?></td>
      <td><?= $s['cashier_name'] !== null && $s['cashier_name'] !== '' ? e($s['cashier_name']) : '<span class="mut">—</span>' ?></td>
      <td><strong><?= e(money((int)$s['total_cents'], $currency)) ?></strong><br><span class="mut" style="font-size:.72rem"><?= e((string)$s['payment_type']) ?></span></td>
      <td><span class="badge <?= $s['status'] === 'completed' ? 'b-ok' : 'b-warn' ?>"><?= e((string)$s['status']) ?></span></td>
     </tr>
    <?php endforeach; ?>
   </table></div>
  </div>
  <div>
   <h2>Top products</h2>
   <div class="tblwrap"><table>
    <tr><th>Product</th><th>Qty sold</th><th>Revenue</th></tr>
    <?php if ($topProducts === []): ?><tr><td colspan="3" class="mut">No completed sales yet.</td></tr><?php endif; ?>
    <?php foreach ($topProducts as $p): ?>
     <tr>
      <td><?= e((string)$p['name']) ?></td>
      <td><?= e((string)round((float)$p['qty'], 2)) ?></td>
      <td><strong><?= e(money((int)$p['total'], $currency)) ?></strong></td>
     </tr>
    <?php endforeach; ?>
   </table></div>

   <h2>Low stock (≤ <?= e((string)$lowThreshold) ?>)</h2>
   <div class="tblwrap"><table>
    <tr><th>Product</th><th>Stock</th><th>Price</th></tr>
    <?php if ($lowStock === []): ?><tr><td colspan="3" class="mut">Everything is stocked up 👍</td></tr><?php endif; ?>
    <?php foreach ($lowStock as $p): ?>
     <tr>
      <td><?= e((string)$p['name']) ?></td>
      <td><span class="badge <?= (float)$p['stock'] <= 0 ? 'b-err' : 'b-warn' ?>"><?= e((string)round((float)$p['stock'], 2)) ?></span></td>
      <td class="mut"><?= e(money((int)$p['price_cents'], $currency)) ?></td>
     </tr>
    <?php endforeach; ?>
   </table></div>
  </div>
 </div>

 <h2>Staff accounts</h2>
 <div class="tblwrap"><table>
  <tr><th>Email</th><th>Name</th><th>Role</th><th>Status</th><th>Created</th></tr>
  <?php if ($users === []): ?><tr><td colspan="5" class="mut">No accounts yet — create them in <a href="devices.php">devices.php</a>.</td></tr><?php endif; ?>
  <?php foreach ($users as $us): ?>
   <tr>
    <td><strong><?= e($us['email']) ?></strong></td>
    <td><?= $us['name'] !== null ? e($us['name']) : '<span class="mut">—</span>' ?></td>
    <td><span class="badge <?= $us['role'] === 'owner' ? 'b-ok' : 'b-info' ?>"><?= e($us['role']) ?></span></td>
    <td><span class="badge <?= $us['status'] === 'active' ? 'b-ok' : 'b-err' ?>"><?= e($us['status']) ?></span></td>
    <td class="mut"><?= e(ago((int)$us['created_ms'])) ?></td>
   </tr>
  <?php endforeach; ?>
 </table></div>
 <p class="mut" style="font-size:.82rem;margin-top:.5rem">Add staff, reset passwords, disable accounts and pair terminals in
 <a href="devices.php">Devices &amp; staff accounts</a>.</p>

 <h2>Salary payments</h2>
 <div class="grid" style="grid-template-columns:repeat(3,1fr)">
  <div class="card"><div class="k">Paid this month</div><div class="v"><?= e(money($salaryMonth, $currency)) ?></div><div class="d"><?= date('F Y') ?></div></div>
  <div class="card"><div class="k">Paid all time</div><div class="v"><?= e(money($salaryTotal, $currency)) ?></div><div class="d">recorded in the app's Back Office</div></div>
  <div class="card"><div class="k">Entries</div><div class="v"><?= $salaryCount ?></div><div class="d">payment records synced</div></div>
 </div>

 <div class="two" style="margin-top:1.2rem">
  <div>
   <h2>Per employee</h2>
   <div class="tblwrap"><table>
    <tr><th>Employee</th><th>Payments</th><th>Total paid</th><th>Last paid</th></tr>
    <?php if ($salaryByStaff === []): ?><tr><td colspan="4" class="mut">No salary payments recorded yet — record them in the app: Back Office → Admin → Salary Payments.</td></tr><?php endif; ?>
    <?php foreach ($salaryByStaff as $p): ?>
     <tr>
      <td><strong><?= e((string)$p['label']) ?></strong><br><span class="mut" style="font-size:.75rem"><?= e((string)$p['staff_email']) ?></span></td>
      <td><?= (int)$p['n'] ?></td>
      <td><strong><?= e(money((int)$p['total'], $currency)) ?></strong></td>
      <td class="mut"><?= e(ago((int)$p['last_paid'])) ?></td>
     </tr>
    <?php endforeach; ?>
   </table></div>
  </div>
  <div>
   <h2>Payment log</h2>
   <div class="tblwrap"><table>
    <tr><th>Employee</th><th>Amount</th><th>Note</th><th>By</th><th>When</th></tr>
    <?php if ($salaryRecent === []): ?><tr><td colspan="5" class="mut">Nothing recorded yet.</td></tr><?php endif; ?>
    <?php foreach ($salaryRecent as $p): ?>
     <tr>
      <td><?= e($p['staff_name'] !== '' ? $p['staff_name'] : $p['staff_email']) ?></td>
      <td><strong><?= e(money((int)$p['amount_cents'], $p['currency_symbol'] !== '' ? $p['currency_symbol'] : $currency)) ?></strong></td>
      <td class="mut"><?= $p['note'] !== '' ? e($p['note']) : '—' ?></td>
      <td class="mut"><?= $p['paid_by_name'] !== '' ? e($p['paid_by_name']) : '—' ?></td>
      <td class="mut"><?= e(ago((int)$p['paid_at'])) ?></td>
     </tr>
    <?php endforeach; ?>
   </table></div>
  </div>
 </div>

 <div class="foot">
  <span>LoyPOS admin · data synced from POS terminals · <?= date('Y-m-d H:i') ?></span>
  <span><a href="devices.php">Devices &amp; accounts</a> · <a href="index.php?route=/health">API health</a></span>
 </div>

<?php endif; ?>
</div>
</body>
</html>
