INSERT INTO account (owner_id, account_type)
VALUES ('SYSTEM', 'EXTERNAL'),
       ('SYSTEM', 'USAGE'),
       ('SYSTEM', 'PLATFORM_LOSS');

INSERT INTO account_balance (account_id, balance_micros, allow_negative)
SELECT id, 0, account_type = 'EXTERNAL'
FROM account
WHERE owner_id = 'SYSTEM';