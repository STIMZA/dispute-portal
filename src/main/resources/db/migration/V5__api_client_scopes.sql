-- Each API key is limited to the scopes it needs (least privilege). Existing clients could only load
-- transactions, so they keep exactly that permission.
alter table api_clients add column scopes varchar(200) default 'transactions:load' not null;
