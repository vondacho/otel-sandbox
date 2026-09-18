create table orders (
    id               uuid primary key,
    sku              varchar(32)    not null,
    quantity         integer        not null,
    unit_price       numeric(12, 2) not null,
    total_amount     numeric(12, 2),
    currency         varchar(3)     not null,
    status           varchar(16)    not null,
    rejection_reason varchar(64),
    created_at       timestamp with time zone not null
);

create index orders_created_at_idx on orders (created_at desc);

create table stock_levels (
    sku        varchar(32) primary key,
    available  bigint      not null,
    warehouse  varchar(64),
    updated_at timestamp with time zone
);
