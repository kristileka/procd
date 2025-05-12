create table account
(
    id                 uuid                not null
        constraint account_pk
            primary key,
    realm              varchar(64)         not null,
    email              varchar             not null,
    assigned_groups    character varying[] not null,
    assigned_roles     character varying[] not null,
    registered_at      timestamp default now(),
    last_authorized_at timestamp
);

alter table account
    owner to procd;

create unique index accounts_realm_user
    on account (realm, email);


create table customer
(
    id           uuid                                       not null
        constraint customer_pk
            primary key,
    account_id   uuid,
    first_name   varchar                                    not null,
    last_name    varchar                                    not null,
    phone_code   varchar(5) default NULL::character varying not null,
    phone_number varchar    default NULL::character varying not null
);

alter table customer
    owner to procd;

create index idx_customer_full_name
    on customer (first_name, last_name);

create index idx_customer_phone
    on customer (phone_code, phone_number);

CREATE TABLE IF NOT EXISTS infrastructure_migrations_keycloak
(
    id           uuid
        CONSTRAINT infrastructure_migrations_keycloak_pk PRIMARY KEY,
    version_tag  integer NOT NULL,
    description  varchar NOT NULL,
    script       varchar NOT NULL,
    installed_on timestamp,
    checksum     varchar NOT NULL
);

CREATE TABLE IF NOT EXISTS infrastructure_mutex
(
    id          uuid
        CONSTRAINT infrastructure_mutex_pk PRIMARY KEY,
    expire_date timestamp NOT NULL
);

CREATE INDEX IF NOT EXISTS infrastructure_mutex_search ON infrastructure_mutex (id, expire_date);


create table company
(
    id                      uuid        not null
        constraint company_pk primary key,
    name                    varchar(64) not null,
    subscription_type       varchar(64) not null,
    subscription_expiration time        not null,
    left_subscription_count integer     not null
);


create table customer_company
(
    company_id  uuid       not null,
    customer_id uuid       not null,
    permission  varchar(8) not null
);

CREATE INDEX IF NOT EXISTS company_search ON company (name);
CREATE INDEX IF NOT EXISTS company_search_type ON company (subscription_type);

create table subscription
(
    id         uuid        not null
        constraint subscription_pk primary key,
    company_id uuid        not null,
    date       varchar(64) not null,
    type       varchar(64) not null,
    expiration varchar(64) not null
);

CREATE INDEX IF NOT EXISTS subscription_search ON subscription (type);

create table subscription_history
(
    id         uuid        not null
        constraint subscription_history_pk primary key,
    company_id uuid        not null,
    type       varchar(64) not null,
    expiration timestamp   not null
);

create table definitions
(
    id                 uuid        not null
        constraint account_pk
            primary key,
    company_id         uuid        not null,
    column_name        varchar(64) not null,
    class_type         varchar(64) not null,
    class_name         varchar(64) not null,
    table_name         varchar(64) not null,
    search_value       varchar(64) not null,
    searchable         boolean     not null default true,
    last_authorized_at timestamp
);
CREATE INDEX IF NOT EXISTS definitions_search_company ON definitions (company_id);
CREATE INDEX IF NOT EXISTS definitions_search_table_name_company ON definitions (table_name, company_id);
CREATE INDEX IF NOT EXISTS definitions_search_class_and_name ON definitions (class_type, class_name);
