set query_group = superuser;
WITH
cte_date_list AS
    (
    select day_date
      from dim_date
     where day_date between current_date - interval '1 month' and current_date),
cte_date_network_list AS
    (
    select dn.network_key, dn.network_name, dt.day_date
      from
        cte_date_list dt
            cross join
        (
            select * from dim_network where network_name not in ('Google Affiliate Network', 'Groupon', 'None', 'Unknown')
        ) as dn
    )
select v.* from (
    select
        cdn.network_name,
        cdn.day_date,
        count(day_date) over (partition by network_name) as num_missing_days,
        fc.*
      from
        cte_date_network_list cdn
    left outer join
        (select
            network_key,
            date_trunc('day', commission_date)  as commission_date,
            count(*)                            as record_count,
            sum(transaction_amt)                as transaction_amt_sum,
            sum(commission_amt)                 as commission_amt_sum
           from
            fact_commission_detail fcd
          where
            commission_date >= current_date - interval '1 month'
         group by network_key, date_trunc('day', commission_date)) as fc
    on
        cdn.network_key     = fc.network_key            and
        cdn.day_date        = fc.commission_date
     where
        (
        fc.network_key          is null and
        fc.commission_date      is null
        )
    order by network_name, day_date desc
) as v
where 
    num_missing_days >
        case
        when network_name in ('Apple', 'Avantlink') then
            2
        when network_name in ('Skimlinks') then 
            1
        else 
            0
        end;

;

