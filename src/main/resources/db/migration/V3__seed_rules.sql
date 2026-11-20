-- Keywords are the merchant strings that appear on Serbian statements, so they are
-- data rather than vocabulary and stay in Serbian.
--
-- Priorities leave room between values. A later, more specific rule can be slotted
-- above an existing one without renumbering everything beneath it.

insert into category_rules (keyword, category_id, priority)
select v.keyword, c.id, v.priority
from (values
    ('PLATA',    'Salary',        100),
    ('POVRACAJ', 'Refunds',       100),
    ('MAXI',     'Food',           50),
    ('LIDL',     'Food',           50),
    ('EPS',      'Utilities',      50),
    ('BUS PLUS', 'Transport',      50),
    ('NETFLIX',  'Entertainment',  50)
) as v (keyword, category_name, priority)
join categories c on c.name = v.category_name;
