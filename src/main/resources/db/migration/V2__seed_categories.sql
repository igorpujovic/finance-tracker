-- Reference data, not demo data. A finance tracker with no categories cannot
-- demonstrate categorization, and these are the buckets any user of one wants.
-- Names are English because the project's vocabulary is English; the keywords in
-- V3 are Serbian because they match what a Serbian bank actually prints.

insert into categories (name, type) values
    ('Salary', 'INCOME'),
    ('Refunds', 'INCOME'),
    ('Food', 'EXPENSE'),
    ('Utilities', 'EXPENSE'),
    ('Transport', 'EXPENSE'),
    ('Entertainment', 'EXPENSE'),
    ('Health', 'EXPENSE');
