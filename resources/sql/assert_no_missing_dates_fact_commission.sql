/**
 * Retrieve fact commission data, grouped by network, for the past $n days
 * that have missing revenue.
 */

-- Ensure monitoring query runs in a Workload Management (WLM) queue separate from
-- the ETL queries which populate the tables being monitored.
SET query_group = superuser;

WITH
cte_date_list AS (
    -- Factor out expected dates (to be used as the driving result set, ensuring
    -- that we can detect missing days from the table being monitored)
    select date_key, day_date
      from public.dim_date
     where day_date between current_date - interval '1 month' and current_date),
cte_date_network_list AS (
    -- Cartesian join to produce a driving result set consisting of each day in
    -- the evalution date range for each active network
    select dn.network_key, dn.network_name, dt.date_key, dt.day_date
      from
        cte_date_list dt
        cross join (
            select * from public.dim_network where network_name not in ('Google Affiliate Network', 'Groupon', 'None', 'Unknown')
        ) as dn
    )
select v.* from (
    -- Using the CTE with the expected network / day combinations,
    -- outer join to network / day level aggregated commission data
    select
        cdn.network_name,
        cdn.day_date,
        count(day_date) over (partition by network_name) as num_missing_days,   -- Missing days per network
        fc.*
      from
        cte_date_network_list cdn
    left outer join (
        -- Calculate currently present commission data
        select
            network_key,
            commission_date_key,
            count(*)                as record_count,
            sum(transaction_amt)    as transaction_amt_sum,
            sum(commission_amt)     as commission_amt_sum
           from
            public.fact_commission fc
          where
            commission_date_key >= current_date - interval '1 month'
         group by network_key, commission_date_key) as fc
     on cdn.network_key     = fc.network_key            and
        cdn.date_key        = fc.commission_date_key
     where (
        -- Filter out network / days that have the expected data
        fc.network_key          is null and
        fc.commission_date_key  is null)
order by network_name, day_date desc
) as v
-- Filter out networks that routinely and expectedly provide us with delayed / lagged commission data
where 
    num_missing_days >
        case
        when network_name in ('Apple', 'Avantlink') then
            2
        when network_name in ('Skimlinks') then 
            1
        else 
            -- Default: If not specifically excepted; keep networks / days that have more than 0 missing days
            0
        end;

