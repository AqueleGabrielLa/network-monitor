ALTER TABLE scan_port ADD COLUMN banner TEXT;
ALTER TABLE scan_port ADD COLUMN service TEXT;

ALTER TABLE device ADD COLUMN os_guess TEXT;
