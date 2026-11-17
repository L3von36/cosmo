# LoyPOS Plesk MySQL Backend (`novelsapps.com.et`)

Your Plesk hosting panel currently shows **"Database is not created"** and **SSH access: Forbidden**, with SSL (`Let's Encrypt`) and FastCGI enabled on `httpdocs`.

Follow these steps to finish connecting LoyPOS to your Plesk database:

1. **Create the MySQL/MariaDB Database in Plesk**
   - In Plesk (`novelsapps.com.et` -> **Databases** -> **Add database**):
     - Create a database (for example, `loypos_db`) and a database user.
     - Use a fresh, strong password (rotate the password you pasted in chat).
   - Open **phpMyAdmin** for that database and import `plesk-backend/schema.sql`.

2. **Deploy the PHP REST Sync API via Plesk File Manager or FTP**
   - Because SSH is disabled on your account (`novelsap` @ `213.55.96.154`), upload `plesk-backend/api/v1/` into `httpdocs/api/v1/` using Plesk **File Manager** or FTP.
   - Inside `httpdocs/api/v1/`, copy `config.example.php` to `config.php` and fill in:
     - `db_name`, `db_user`, `db_pass` from Step 1
     - `sync_token`: generate a random 32+ char token

3. **Configure AI Studio Secrets**
   - Open the **Secrets panel in AI Studio** and set `PLESK_SYNC_TOKEN` to the exact `sync_token` you put in `config.php`.
   - Never put database passwords or tokens inside `local.properties` or committed files.

4. **Verify Sync**
   - Visit `https://novelsapps.com.et/api/v1/health` (with `Authorization: Bearer <token>`) or tap **Sync with Plesk MySQL** in LoyPOS Settings / top toolbar.
