SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE datname = 'product_db'
AND pid != pg_backend_pid();
