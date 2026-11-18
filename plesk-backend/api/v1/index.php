<?php
declare(strict_types=1);

header('Content-Type: application/json; charset=utf-8');

function json_response(int $status, array $payload): void {
    http_response_code($status);
    echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
    exit;
}

$configPath = __DIR__ . '/config.php';
if (!file_exists($configPath)) {
    json_response(503, [
        'ok' => false,
        'error' => 'Server not configured: copy config.example.php to config.php in httpdocs/api/v1/',
    ]);
}

$config = require $configPath;

function read_bearer_token(): string {
    $auth = $_SERVER['HTTP_AUTHORIZATION']
        ?? $_SERVER['REDIRECT_HTTP_AUTHORIZATION']
        ?? '';
    if (stripos($auth, 'Bearer ') === 0) {
        return trim(substr($auth, 7));
    }
    return trim((string)($_SERVER['HTTP_X_SYNC_TOKEN'] ?? ''));
}

$providedToken = read_bearer_token();

try {
    $dsn = sprintf(
        'mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4',
        $config['db_host'] ?? 'localhost',
        (int)($config['db_port'] ?? 3306),
        $config['db_name'] ?? ''
    );
    $pdo = new PDO($dsn, (string)($config['db_user'] ?? ''), (string)($config['db_pass'] ?? ''), [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
    ]);
} catch (Throwable $e) {
    json_response(503, [
        'ok' => false,
        'error' => 'MySQL connection failed: ensure database is created in Plesk and schema.sql is imported',
    ]);
}

$uriPath = parse_url($_SERVER['REQUEST_URI'] ?? '/', PHP_URL_PATH) ?: '/';
$route = preg_replace('#^.*/api/v1#', '', $uriPath);
if ($route === '' || $route === false) {
    $route = $_GET['route'] ?? '/health';
}
$method = strtoupper($_SERVER['REQUEST_METHOD'] ?? 'GET');
$serverTimeMs = (int) round(microtime(true) * 1000);

// ── Tier 2 device auth ──────────────────────────────────────────────────────
// Each terminal pairs once (POST /pair with a one-time code from devices.php)
// and receives its own long-lived device token, stored only as a SHA-256 hash
// in `devices`. The legacy shared sync_token stays accepted as a fallback
// until `require_device_auth` is set to '1' in config.php (toggle in devices.php).
function authenticate_request(PDO $pdo, string $providedToken, array $config): array {
    $legacyToken = (string)($config['sync_token'] ?? '');
    $requireDevice = (string)($config['require_device_auth'] ?? '0') === '1';
    if (
        !$requireDevice && $providedToken !== ''
        && $legacyToken !== '' && $legacyToken !== 'YOUR_PLESK_SYNC_TOKEN'
        && hash_equals($legacyToken, $providedToken)
    ) {
        return ['mode' => 'legacy', 'device' => null];
    }
    if ($providedToken !== '') {
        $device = false;
        try {
            $stmt = $pdo->prepare('SELECT id, name, branch_id, status FROM devices WHERE token_hash = :h LIMIT 1');
            $stmt->execute([':h' => hash('sha256', $providedToken)]);
            $device = $stmt->fetch();
        } catch (Throwable $e) {
            $device = false; // devices table not migrated yet — fall through
        }
        if ($device !== false) {
            if ($device['status'] === 'active') {
                $pdo->prepare('UPDATE devices SET last_seen_ms = :t WHERE id = :id')
                    ->execute([':t' => (int) round(microtime(true) * 1000), ':id' => (int) $device['id']]);
                $device['id'] = (int) $device['id'];
                return ['mode' => 'device', 'device' => $device];
            }
            json_response(401, ['ok' => false, 'error' => 'This device has been revoked', 'device_revoked' => true]);
        }
    }
    json_response(401, ['ok' => false, 'error' => $requireDevice
        ? 'Unauthorized: sign in on this terminal first (app Settings → Sign In & Pair This Device) or use a valid device token'
        : 'Unauthorized: invalid sync or device token']);
}

// POST /devices/register — self-registration. The terminal signs in with an
// owner/manager account (email + password) and receives its own long-lived
// device token. No bearer auth here (a brand-new terminal has no token yet);
// protected instead by bcrypt password verification + per-IP throttling.
// Passwords are never stored — only the password_hash from the users table.
if ($method === 'POST' && ($route === '/devices/register' || $route === '/devices/pair')) {
    $ipKey = 'loypos_reg_' . hash('sha256', (string)($_SERVER['REMOTE_ADDR'] ?? 'unknown'));
    $tmpFile = sys_get_temp_dir() . '/' . $ipKey;
    $hits = is_file($tmpFile) ? (json_decode((string)file_get_contents($tmpFile), true) ?: []) : [];
    $hits = array_values(array_filter($hits, static fn($t) => $t > $serverTimeMs - 60000));
    if (count($hits) >= 10) {
        json_response(429, ['ok' => false, 'error' => 'Too many sign-in attempts from this address — try again in a minute']);
    }
    $hits[] = $serverTimeMs;
    @file_put_contents($tmpFile, json_encode($hits), LOCK_EX);

    $data = json_decode(file_get_contents('php://input') ?: '{}', true);
    if (!is_array($data)) {
        json_response(400, ['ok' => false, 'error' => 'Invalid JSON body']);
    }
    $email = strtolower(trim((string)($data['email'] ?? '')));
    $password = (string)($data['password'] ?? '');
    $deviceName = mb_substr(trim((string)($data['name'] ?? '')), 0, 100);
    if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
        json_response(400, ['ok' => false, 'error' => 'Enter the email of your owner or manager account']);
    }
    if ($password === '') {
        json_response(400, ['ok' => false, 'error' => 'Account password required']);
    }
    if ($deviceName === '') {
        $deviceName = 'Terminal';
    }
    try {
        $stmt = $pdo->prepare('SELECT id, email, password_hash, role, status FROM users WHERE email = :e LIMIT 1');
        $stmt->execute([':e' => $email]);
        $user = $stmt->fetch();
    } catch (Throwable $e) {
        json_response(503, ['ok' => false, 'error' => 'Account tables missing — re-run install.php (migration step 003_account_auth)']);
    }
    if (!$user || !password_verify($password, (string)$user['password_hash'])) {
        usleep(400000); // slow down guessing without leaking which field failed
        json_response(401, ['ok' => false, 'error' => 'Invalid email or password']);
    }
    if ($user['status'] !== 'active') {
        json_response(403, ['ok' => false, 'error' => 'This account is disabled — ask the shop owner']);
    }
    if (!in_array($user['role'], ['owner', 'manager'], true)) {
        json_response(403, ['ok' => false, 'error' => 'Only owner or manager accounts can add a terminal']);
    }
    $deviceToken = bin2hex(random_bytes(32)); // returned once, stored hashed
    try {
        $pdo->beginTransaction();
        $ins = $pdo->prepare(
            "INSERT INTO devices (name, branch_id, token_hash, paired_by_user_id, status, last_seen_ms, created_ms)
             VALUES (:n, NULL, :h, :uid, 'active', :seen, :made)"
        );
        $ins->execute([
            ':n' => $deviceName,
            ':h' => hash('sha256', $deviceToken),
            ':uid' => (int)$user['id'],
            ':seen' => $serverTimeMs,
            ':made' => $serverTimeMs,
        ]);
        $deviceId = (int)$pdo->lastInsertId();
        $pdo->commit();
    } catch (Throwable $e) {
        if ($pdo->inTransaction()) {
            $pdo->rollBack();
        }
        error_log('LoyPOS device register failed: ' . $e->getMessage());
        json_response(500, ['ok' => false, 'error' => 'Could not register this device — try again']);
    }
    json_response(201, [
        'ok' => true,
        'device_id' => $deviceId,
        'device_name' => $deviceName,
        'device_token' => $deviceToken,
        'token_type' => 'device',
        'paired_as' => (string)$user['email'],
        'server_time_ms' => $serverTimeMs,
    ]);
}

// POST /devices/verify — credential check for the app's sign-in gate. The
// terminal asks "is this email + password a valid account?" WITHOUT creating
// a device (register does that) and WITHOUT receiving any token. Returns the
// role so the app can mirror the account locally for offline sign-ins.
// Shares the per-IP throttle bucket with /devices/register.
if ($method === 'POST' && $route === '/devices/verify') {
    $ipKey = 'loypos_reg_' . hash('sha256', (string)($_SERVER['REMOTE_ADDR'] ?? 'unknown'));
    $tmpFile = sys_get_temp_dir() . '/' . $ipKey;
    $hits = is_file($tmpFile) ? (json_decode((string)file_get_contents($tmpFile), true) ?: []) : [];
    $hits = array_values(array_filter($hits, static fn($t) => $t > $serverTimeMs - 60000));
    if (count($hits) >= 10) {
        json_response(429, ['ok' => false, 'error' => 'Too many sign-in attempts from this address — try again in a minute']);
    }
    $hits[] = $serverTimeMs;
    @file_put_contents($tmpFile, json_encode($hits), LOCK_EX);

    $data = json_decode(file_get_contents('php://input') ?: '{}', true);
    if (!is_array($data)) {
        json_response(400, ['ok' => false, 'error' => 'Invalid JSON body']);
    }
    $email = strtolower(trim((string)($data['email'] ?? '')));
    $password = (string)($data['password'] ?? '');
    if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
        json_response(400, ['ok' => false, 'error' => 'Enter your account email']);
    }
    if ($password === '') {
        json_response(400, ['ok' => false, 'error' => 'Account password required']);
    }
    try {
        $stmt = $pdo->prepare('SELECT id, email, password_hash, role, status, name FROM users WHERE email = :e LIMIT 1');
        $stmt->execute([':e' => $email]);
        $user = $stmt->fetch();
    } catch (Throwable $e) {
        json_response(503, ['ok' => false, 'error' => 'Account tables missing — re-run install.php (migration step 003_account_auth)']);
    }
    if (!$user || !password_verify($password, (string)$user['password_hash'])) {
        usleep(400000); // slow down guessing without leaking which field failed
        json_response(401, ['ok' => false, 'error' => 'Invalid email or password']);
    }
    if ($user['status'] !== 'active') {
        json_response(403, ['ok' => false, 'error' => 'This account is disabled — ask the shop owner']);
    }
    json_response(200, [
        'ok' => true,
        'email' => (string)$user['email'],
        'name' => (string)($user['name'] ?? ''),
        'role' => (string)$user['role'],
        'server_time_ms' => $serverTimeMs,
    ]);
}

// Everything below requires a valid credential (device token or legacy token)
$auth = authenticate_request($pdo, $providedToken, $config);

if ($method === 'GET' && ($route === '/health' || $route === '/')) {
    $pdo->query('SELECT 1');
    $counts = [
        'branches' => (int) $pdo->query('SELECT COUNT(*) FROM branches')->fetchColumn(),
        'products' => (int) $pdo->query('SELECT COUNT(*) FROM products')->fetchColumn(),
        'sales' => (int) $pdo->query('SELECT COUNT(*) FROM sales')->fetchColumn(),
    ];
    json_response(200, [
        'ok' => true,
        'status' => 'ok',
        'database' => 'mysql',
        'server_time_ms' => $serverTimeMs,
        'auth' => [
            'mode' => $auth['mode'],
            'device_id' => $auth['device']['id'] ?? null,
            'device_name' => $auth['device']['name'] ?? null,
        ],
        'counts' => $counts,
    ]);
}

if ($method === 'GET' && $route === '/devices/me') {
    if ($auth['mode'] !== 'device') {
        json_response(200, ['ok' => true, 'mode' => 'legacy', 'message' => 'Running on the legacy shared token — sign in with an owner account (Settings → Sign In & Pair This Device) to give this terminal its own credentials']);
    }
    $pairedAs = null;
    try {
        $q = $pdo->prepare('SELECT paired_by_user_id FROM devices WHERE id = :id LIMIT 1');
        $q->execute([':id' => (int)$auth['device']['id']]);
        $pairedBy = (int)($q->fetchColumn() ?: 0);
        if ($pairedBy > 0) {
            $q2 = $pdo->prepare('SELECT email FROM users WHERE id = :id LIMIT 1');
            $q2->execute([':id' => $pairedBy]);
            $pairedAs = $q2->fetchColumn() ?: null;
        }
    } catch (Throwable $e) {
        $pairedAs = null; // users table / new column not migrated yet
    }
    json_response(200, ['ok' => true, 'mode' => 'device', 'device' => $auth['device'], 'paired_as' => $pairedAs]);
}

if ($method === 'POST' && $route === '/sync/push') {
    $rawBody = file_get_contents('php://input');
    $data = json_decode($rawBody ?: '{}', true);
    if (!is_array($data)) {
        json_response(400, ['ok' => false, 'error' => 'Invalid JSON body']);
    }

    $upserted = 0;
    try {
        $pdo->beginTransaction();

        if (!empty($data['branches']) && is_array($data['branches'])) {
            $stmt = $pdo->prepare(
                'INSERT INTO branches (id, name, address, phone, created_at, archived, updated_at)
                 VALUES (:id, :name, :address, :phone, :created_at, :archived, :updated_at)
                 ON DUPLICATE KEY UPDATE
                 name = VALUES(name), address = VALUES(address), phone = VALUES(phone),
                 archived = VALUES(archived), updated_at = VALUES(updated_at)'
            );
            foreach ($data['branches'] as $r) {
                $stmt->execute([
                    ':id' => (string)$r['id'],
                    ':name' => (string)$r['name'],
                    ':address' => (string)($r['address'] ?? ''),
                    ':phone' => (string)($r['phone'] ?? ''),
                    ':created_at' => (int)($r['created_at'] ?? 0),
                    ':archived' => (int)($r['archived'] ?? 0),
                    ':updated_at' => (int)($r['updated_at'] ?? 0),
                ]);
                $upserted++;
            }
        }

        if (!empty($data['categories']) && is_array($data['categories'])) {
            $stmt = $pdo->prepare(
                'INSERT INTO categories (id, name, position, updated_at)
                 VALUES (:id, :name, :position, :updated_at)
                 ON DUPLICATE KEY UPDATE
                 name = VALUES(name), position = VALUES(position), updated_at = VALUES(updated_at)'
            );
            foreach ($data['categories'] as $r) {
                $stmt->execute([
                    ':id' => (string)$r['id'],
                    ':name' => (string)$r['name'],
                    ':position' => (int)($r['position'] ?? 0),
                    ':updated_at' => (int)($r['updated_at'] ?? 0),
                ]);
                $upserted++;
            }
        }

        if (!empty($data['products']) && is_array($data['products'])) {
            $stmt = $pdo->prepare(
                'INSERT INTO products (id, category_id, name, price_cents, cost_cents, sku, barcode, track_stock, stock, archived, branch_ids, image_path, updated_at)
                 VALUES (:id, :category_id, :name, :price_cents, :cost_cents, :sku, :barcode, :track_stock, :stock, :archived, :branch_ids, :image_path, :updated_at)
                 ON DUPLICATE KEY UPDATE
                 category_id = VALUES(category_id), name = VALUES(name), price_cents = VALUES(price_cents),
                 cost_cents = VALUES(cost_cents), sku = VALUES(sku), barcode = VALUES(barcode),
                 track_stock = VALUES(track_stock), stock = VALUES(stock), archived = VALUES(archived),
                 branch_ids = VALUES(branch_ids), image_path = VALUES(image_path), updated_at = VALUES(updated_at)'
            );
            foreach ($data['products'] as $r) {
                $stmt->execute([
                    ':id' => (string)$r['id'],
                    ':category_id' => $r['category_id'] ?? null,
                    ':name' => (string)$r['name'],
                    ':price_cents' => (int)($r['price_cents'] ?? 0),
                    ':cost_cents' => (int)($r['cost_cents'] ?? 0),
                    ':sku' => (string)($r['sku'] ?? ''),
                    ':barcode' => (string)($r['barcode'] ?? ''),
                    ':track_stock' => (int)($r['track_stock'] ?? 0),
                    ':stock' => (float)($r['stock'] ?? 0.0),
                    ':archived' => (int)($r['archived'] ?? 0),
                    ':branch_ids' => (string)($r['branch_ids'] ?? ''),
                    ':image_path' => (string)($r['image_path'] ?? ''),
                    ':updated_at' => (int)($r['updated_at'] ?? 0),
                ]);
                $upserted++;
            }
        }

        if (!empty($data['customers']) && is_array($data['customers'])) {
            $stmt = $pdo->prepare(
                'INSERT INTO customers (id, name, phone, email, note, points, created_at, updated_at)
                 VALUES (:id, :name, :phone, :email, :note, :points, :created_at, :updated_at)
                 ON DUPLICATE KEY UPDATE
                 name = VALUES(name), phone = VALUES(phone), email = VALUES(email),
                 note = VALUES(note), points = VALUES(points), updated_at = VALUES(updated_at)'
            );
            foreach ($data['customers'] as $r) {
                $stmt->execute([
                    ':id' => (string)$r['id'],
                    ':name' => (string)$r['name'],
                    ':phone' => (string)($r['phone'] ?? ''),
                    ':email' => (string)($r['email'] ?? ''),
                    ':note' => (string)($r['note'] ?? ''),
                    ':points' => (int)($r['points'] ?? 0),
                    ':created_at' => (int)($r['created_at'] ?? 0),
                    ':updated_at' => (int)($r['updated_at'] ?? 0),
                ]);
                $upserted++;
            }
        }

        if (!empty($data['sales']) && is_array($data['sales'])) {
            $stmt = $pdo->prepare(
                'INSERT INTO sales (id, number, created_at, subtotal_cents, discount_cents, discount_type, discount_value, tax_cents, total_cents, paid_cents, change_cents, payment_type, customer_id, customer_name, status, note, branch_id, cashier_id, cashier_name, updated_at)
                 VALUES (:id, :number, :created_at, :subtotal_cents, :discount_cents, :discount_type, :discount_value, :tax_cents, :total_cents, :paid_cents, :change_cents, :payment_type, :customer_id, :customer_name, :status, :note, :branch_id, :cashier_id, :cashier_name, :updated_at)
                 ON DUPLICATE KEY UPDATE
                 status = VALUES(status), note = VALUES(note), updated_at = VALUES(updated_at)'
            );
            foreach ($data['sales'] as $r) {
                $stmt->execute([
                    ':id' => (string)$r['id'],
                    ':number' => (int)($r['number'] ?? 1),
                    ':created_at' => (int)($r['created_at'] ?? 0),
                    ':subtotal_cents' => (int)($r['subtotal_cents'] ?? 0),
                    ':discount_cents' => (int)($r['discount_cents'] ?? 0),
                    ':discount_type' => $r['discount_type'] ?? null,
                    ':discount_value' => $r['discount_value'] ?? null,
                    ':tax_cents' => (int)($r['tax_cents'] ?? 0),
                    ':total_cents' => (int)($r['total_cents'] ?? 0),
                    ':paid_cents' => (int)($r['paid_cents'] ?? 0),
                    ':change_cents' => (int)($r['change_cents'] ?? 0),
                    ':payment_type' => (string)($r['payment_type'] ?? 'cash'),
                    ':customer_id' => $r['customer_id'] ?? null,
                    ':customer_name' => $r['customer_name'] ?? null,
                    ':status' => (string)($r['status'] ?? 'completed'),
                    ':note' => (string)($r['note'] ?? ''),
                    ':branch_id' => $r['branch_id'] ?? null,
                    ':cashier_id' => $r['cashier_id'] ?? null,
                    ':cashier_name' => $r['cashier_name'] ?? null,
                    ':updated_at' => (int)($r['updated_at'] ?? 0),
                ]);
                $upserted++;
            }
        }

        if (!empty($data['sale_items']) && is_array($data['sale_items'])) {
            $stmt = $pdo->prepare(
                'INSERT INTO sale_items (id, receipt_id, item_id, name, price_cents, qty, line_total_cents)
                 VALUES (:id, :receipt_id, :item_id, :name, :price_cents, :qty, :line_total_cents)
                 ON DUPLICATE KEY UPDATE
                 qty = VALUES(qty), line_total_cents = VALUES(line_total_cents)'
            );
            foreach ($data['sale_items'] as $r) {
                $stmt->execute([
                    ':id' => (string)$r['id'],
                    ':receipt_id' => (string)$r['receipt_id'],
                    ':item_id' => (string)$r['item_id'],
                    ':name' => (string)$r['name'],
                    ':price_cents' => (int)($r['price_cents'] ?? 0),
                    ':qty' => (float)($r['qty'] ?? 1.0),
                    ':line_total_cents' => (int)($r['line_total_cents'] ?? 0),
                ]);
                $upserted++;
            }
        }

        if (!empty($data['stock_movements']) && is_array($data['stock_movements'])) {
            $stmt = $pdo->prepare(
                'INSERT IGNORE INTO stock_movements (id, item_id, qty, type, created_at, note, receipt_id)
                 VALUES (:id, :item_id, :qty, :type, :created_at, :note, :receipt_id)'
            );
            foreach ($data['stock_movements'] as $r) {
                $stmt->execute([
                    ':id' => (string)$r['id'],
                    ':item_id' => (string)$r['item_id'],
                    ':qty' => (float)($r['qty'] ?? 0.0),
                    ':type' => (string)($r['type'] ?? 'adjust'),
                    ':created_at' => (int)($r['created_at'] ?? 0),
                    ':note' => (string)($r['note'] ?? ''),
                    ':receipt_id' => $r['receipt_id'] ?? null,
                ]);
                $upserted++;
            }
        }

        if (!empty($data['shifts']) && is_array($data['shifts'])) {
            $stmt = $pdo->prepare(
                'INSERT INTO shifts (id, opened_at, opening_float_cents, branch_id, closed_at, counted_cents, expected_cents)
                 VALUES (:id, :opened_at, :opening_float_cents, :branch_id, :closed_at, :counted_cents, :expected_cents)
                 ON DUPLICATE KEY UPDATE
                 closed_at = VALUES(closed_at), counted_cents = VALUES(counted_cents), expected_cents = VALUES(expected_cents)'
            );
            foreach ($data['shifts'] as $r) {
                $stmt->execute([
                    ':id' => (string)$r['id'],
                    ':opened_at' => (int)($r['opened_at'] ?? 0),
                    ':opening_float_cents' => (int)($r['opening_float_cents'] ?? 0),
                    ':branch_id' => $r['branch_id'] ?? null,
                    ':closed_at' => $r['closed_at'] ?? null,
                    ':counted_cents' => $r['counted_cents'] ?? null,
                    ':expected_cents' => $r['expected_cents'] ?? null,
                ]);
                $upserted++;
            }
        }

        if (!empty($data['cash_movements']) && is_array($data['cash_movements'])) {
            $stmt = $pdo->prepare(
                'INSERT IGNORE INTO cash_movements (id, shift_id, type, amount_cents, note, created_at)
                 VALUES (:id, :shift_id, :type, :amount_cents, :note, :created_at)'
            );
            foreach ($data['cash_movements'] as $r) {
                $stmt->execute([
                    ':id' => (string)$r['id'],
                    ':shift_id' => (string)$r['shift_id'],
                    ':type' => (string)($r['type'] ?? 'in'),
                    ':amount_cents' => (int)($r['amount_cents'] ?? 0),
                    ':note' => (string)($r['note'] ?? ''),
                    ':created_at' => (int)($r['created_at'] ?? 0),
                ]);
                $upserted++;
            }
        }

        // Salary payments (migration 004). If the table isn't migrated yet,
        // skip it instead of failing the whole push — salary data stays local.
        if (!empty($data['salary_payments']) && is_array($data['salary_payments'])) {
            try {
                $stmt = $pdo->prepare(
                    'INSERT IGNORE INTO salary_payments (id, staff_id, staff_name, staff_email, amount_cents, currency_symbol, note, paid_by_name, paid_at)
                     VALUES (:id, :staff_id, :staff_name, :staff_email, :amount_cents, :currency_symbol, :note, :paid_by_name, :paid_at)'
                );
                foreach ($data['salary_payments'] as $r) {
                    $stmt->execute([
                        ':id' => (string)$r['id'],
                        ':staff_id' => (string)$r['staff_id'],
                        ':staff_name' => (string)($r['staff_name'] ?? ''),
                        ':staff_email' => (string)($r['staff_email'] ?? ''),
                        ':amount_cents' => (int)($r['amount_cents'] ?? 0),
                        ':currency_symbol' => (string)($r['currency_symbol'] ?? ''),
                        ':note' => (string)($r['note'] ?? ''),
                        ':paid_by_name' => (string)($r['paid_by_name'] ?? ''),
                        ':paid_at' => (int)($r['paid_at'] ?? 0),
                    ]);
                    $upserted++;
                }
            } catch (Throwable $e) {
                // salary_payments table not migrated yet — push continues
            }
        }

        if (!empty($data['settings']) && is_array($data['settings'])) {
            $stmt = $pdo->prepare(
                'INSERT INTO shop_settings (setting_key, setting_value, updated_at)
                 VALUES (:setting_key, :setting_value, :updated_at)
                 ON DUPLICATE KEY UPDATE
                 setting_value = VALUES(setting_value), updated_at = VALUES(updated_at)'
            );
            foreach ($data['settings'] as $r) {
                $stmt->execute([
                    ':setting_key' => (string)$r['setting_key'],
                    ':setting_value' => (string)($r['setting_value'] ?? ''),
                    ':updated_at' => $serverTimeMs,
                ]);
            }
        }

        $pdo->commit();
        json_response(200, [
            'ok' => true,
            'upserted' => $upserted,
            'server_time_ms' => $serverTimeMs,
        ]);
    } catch (Throwable $e) {
        if ($pdo->inTransaction()) {
            $pdo->rollBack();
        }
        json_response(500, ['ok' => false, 'error' => 'Sync push failed: ' . $e->getMessage()]);
    }
}

if ($method === 'GET' && $route === '/sync/pull') {
    $since = max(0, (int)($_GET['since'] ?? 0));

    $branchesStmt = $pdo->prepare('SELECT * FROM branches WHERE updated_at >= :s OR created_at >= :s2');
    $branchesStmt->execute([':s' => $since, ':s2' => $since]);

    $catsStmt = $pdo->prepare('SELECT * FROM categories WHERE updated_at >= :s');
    $catsStmt->execute([':s' => $since]);

    $prodsStmt = $pdo->prepare('SELECT * FROM products WHERE updated_at >= :s');
    $prodsStmt->execute([':s' => $since]);

    $custsStmt = $pdo->prepare('SELECT * FROM customers WHERE updated_at >= :s OR created_at >= :s2');
    $custsStmt->execute([':s' => $since, ':s2' => $since]);

    $salesStmt = $pdo->prepare('SELECT * FROM sales WHERE updated_at >= :s OR created_at >= :s2');
    $salesStmt->execute([':s' => $since, ':s2' => $since]);
    $sales = $salesStmt->fetchAll();

    $saleItems = [];
    if (!empty($sales)) {
        $ids = array_column($sales, 'id');
        $placeholders = implode(',', array_fill(0, count($ids), '?'));
        $itemsStmt = $pdo->prepare("SELECT * FROM sale_items WHERE receipt_id IN ($placeholders)");
        $itemsStmt->execute($ids);
        $saleItems = $itemsStmt->fetchAll();
    }

    $movesStmt = $pdo->prepare('SELECT * FROM stock_movements WHERE created_at >= :s');
    $movesStmt->execute([':s' => $since]);

    $shiftsStmt = $pdo->prepare('SELECT * FROM shifts WHERE opened_at >= :s OR (closed_at IS NOT NULL AND closed_at >= :s2)');
    $shiftsStmt->execute([':s' => $since, ':s2' => $since]);

    $cashStmt = $pdo->prepare('SELECT * FROM cash_movements WHERE created_at >= :s');
    $cashStmt->execute([':s' => $since]);

    // Migration 004 may not be applied on older installs — degrade gracefully.
    $salaryPayments = [];
    try {
        $salaryStmt = $pdo->prepare('SELECT * FROM salary_payments WHERE paid_at >= :s');
        $salaryStmt->execute([':s' => $since]);
        $salaryPayments = $salaryStmt->fetchAll();
    } catch (Throwable $e) {
        $salaryPayments = [];
    }

    json_response(200, [
        'ok' => true,
        'server_time_ms' => $serverTimeMs,
        'branches' => $branchesStmt->fetchAll(),
        'categories' => $catsStmt->fetchAll(),
        'products' => $prodsStmt->fetchAll(),
        'customers' => $custsStmt->fetchAll(),
        'sales' => $sales,
        'sale_items' => $saleItems,
        'stock_movements' => $movesStmt->fetchAll(),
        'shifts' => $shiftsStmt->fetchAll(),
        'cash_movements' => $cashStmt->fetchAll(),
        'salary_payments' => $salaryPayments,
    ]);
}

json_response(404, ['ok' => false, 'error' => 'Endpoint not found']);
