<?php
/**
 * LoyPOS Professional Server-Side Proxy Example (proxy.php)
 * 
 * BEST PRACTICE:
 * In professional production architecture, mobile apps never connect directly to databases
 * or embed raw master database credentials. Instead, requests are routed through a secure
 * Server-Side Proxy or API Gateway that validates user sessions (JWT/OAuth), signs requests,
 * and interacts with MySQL/MariaDB securely.
 */

header('Content-Type: application/json; charset=utf-8');

require_once __DIR__ . '/config.php';

// 1. Authenticate Request via Bearer Token or App Check Signature
$headers = getallheaders();
$authHeader = $headers['Authorization'] ?? $headers['authorization'] ?? '';
$syncTokenHeader = $headers['X-Sync-Token'] ?? $headers['x-sync-token'] ?? '';

$token = '';
if (preg_match('/Bearer\s(\S+)/', $authHeader, $matches)) {
    $token = $matches[1];
} elseif (!empty($syncTokenHeader)) {
    $token = $syncTokenHeader;
}

if (!hash_equals(SYNC_TOKEN, $token)) {
    http_response_code(401);
    echo json_encode(['error' => 'Unauthorized: Invalid or missing secure sync token']);
    exit;
}

// 2. Forward request or handle business logic securely via PDO
try {
    $pdo = new PDO(
        "mysql:host=" . DB_HOST . ";dbname=" . DB_NAME . ";charset=utf8mb4",
        DB_USER,
        DB_PASS,
        [
            PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        ]
    );

    $action = $_GET['action'] ?? 'health';
    if ($action === 'health') {
        echo json_encode(['status' => 'healthy', 'proxy' => 'active', 'server_time_ms' => round(microtime(true) * 1000)]);
        exit;
    }

    echo json_encode(['status' => 'ok', 'message' => 'Proxy request handled successfully']);
} catch (Exception $e) {
    http_response_code(503);
    echo json_encode(['error' => 'Database proxy connection error: ' . $e->getMessage()]);
}
