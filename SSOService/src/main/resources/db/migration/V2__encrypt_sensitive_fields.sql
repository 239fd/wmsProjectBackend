ALTER TABLE login_audit
    ALTER COLUMN ip_address TYPE TEXT USING ip_address::TEXT;
