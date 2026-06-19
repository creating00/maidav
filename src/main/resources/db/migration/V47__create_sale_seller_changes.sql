create table sale_seller_changes (
    id bigserial primary key,
    sale_id bigint not null references sales(id),
    previous_seller_id bigint not null references users(id),
    new_seller_id bigint not null references users(id),
    changed_by_user_id bigint not null references users(id),
    created_at timestamp not null default now(),
    updated_at timestamp
);

create index idx_sale_seller_changes_sale_id
    on sale_seller_changes (sale_id, created_at desc, id desc);
