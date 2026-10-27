alter table customers add column role varchar(20) default 'CUSTOMER' not null;

alter table disputes add column updated_by varchar(50);
update disputes set updated_by = created_by where updated_by is null;

create index ix_disputes_created_at on disputes (created_at);
