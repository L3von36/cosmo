<?php
// Copy this file to config.php on your Plesk server inside httpdocs/api/v1/
// NEVER commit config.php with real credentials to version control.

return [
    'db_host' => 'localhost',
    'db_port' => 3306,
    'db_name' => 'YOUR_PLESK_DB_NAME',
    'db_user' => 'YOUR_PLESK_DB_USER',
    'db_pass' => 'YOUR_PLESK_DB_PASSWORD',
    // Set the same secret token as PLESK_SYNC_TOKEN in AI Studio Secrets panel
    'sync_token' => 'YOUR_PLESK_SYNC_TOKEN',
    // Admin gate for install.php / devices.php bootstrap & recovery.
    // The installer keeps or auto-generates this; change it any time here.
    'setup_token' => 'CHANGE_ME_SETUP_TOKEN',
];
