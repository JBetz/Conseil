# Conseil

A blockchain indexer & API for building decentralized applications, currently focused on Tezos.

Conseil is a fundamental part of the [Nautilus](https://github.com/Cryptonomic/Nautilus) infrastructure for high-performance chain analytics. It is the backbone of Cryptonomic's product offerings. The [Arronax](https://arronax-beta.cryptonomic.tech) block explorer and reporting tool, and the [Tezori](https://github.com/Cryptonomic/Tezori)/[Galleon](https://galleon-wallet.tech) wallet are both made possible by Conseil. The aforememtion products are additionally using [ConseilJS](https://github.com/Cryptonomic/ConseilJS) &mdash; a Typescript wrapper with a Tezos node interface.

## Components

Conseil the project consists of several parts. First it requires a data store compatible with the [Slick FRM](http://slick.lightbend.com). At Cryptonomic we use [PostgreSQL](http://postgresql.org). There are two services that will then interact with the database: [lorre](https://github.com/Cryptonomic/Conseil/blob/master/src/main/scala/tech/cryptonomic/conseil/Lorre.scala) and [conseil](https://github.com/Cryptonomic/Conseil/blob/master/src/main/scala/tech/cryptonomic/conseil/Conseil.scala). The former is responsible for keeping the database in sync with the blockchain, the latter handles user requests for that data via a RESTful interface. Generally, `lorre` is the only component that will write to the database. Similarly, `conseil` will only read from the database. Separating the configuration files for these two services with different database credentials would be good security practice as `lorre` has no exposed interface. Finally, a [blockchain node](https://gitlab.com/tezos/tezos) is required.

## Running Conseil

### Requirements

In addition to the database service described above, JRE (>= 8.0) is required to run the Conseil components.

#### Building from Source

Should you chose to compile from source, a Scala development environment will be necessary comprising of the following:

- JDK (> 8.x)
- Scala (> 2.12.x)
- SBT (> 1.2.6)

To package, simply run `sbt -J-Xss32m clean assembly`. This process may take a minute to complete, once it's done, `.jar` will be produced in `/tmp/conseil.jar`. To just compile, run `sbt -J-Xss32m clean compile`.

### Starting

Assuming that the database and blockchain node are up and running, the next thing to start is `lorre`.

```
java -Xms512m -Xmx14g -Dconfig.file=conseil.conf -cp conseil.jar tech.cryptonomic.conseil.Lorre alphanet
```

In this command, the `-Xms512m` and `-Xmx14g` can be changed based on the amount of memory available on the system, the `-Dconfig.file=conseil.conf` can be changed to point to any user created config file that contains the credentials for the respective db and Tezos node. The file can also contain any overrides for settings provided by the `reference.conf` file. Lastly, `alphanet` is the network being run by the Tezos node, it can and should be changed if running `mainnet` or `zeronet`.

Start `conseil` as:

```
java -Xms512m -Xmx2g -Dconfig.file=conseil.conf -cp conseil.jar tech.cryptonomic.conseil.Conseil
```

To run `conseil` with high cardinality attribute values caching required max heap size is `-Xmx4g`, but suggested is `-Xmx8g`. It also depends on amount of high cardinality attributes which are needed to be cached.

A few things to note- `lorre` may take some time to catch up to the current block height depending on how long the chain is. During this process it may require more memory. Incremental updates are quick and not memory intensive after that. `lorre` will write data incrementally to the database, so `conseil` will be usable before it's fully updated.

For examples of how we run these services check out the [Nautilus](https://github.com/Cryptonomic/Nautilus) repo.

To run `lorre` and `conseil` with `sbt`, use the following:

```
env SBT_OPTS="-Dconfig.file=conseil.conf" && sbt -J-Xss32m "runConseil"
env SBT_OPTS="-Dconfig.file=conseil.conf" && sbt -J-Xss32m "runLorre alphanet"
```

### Logging

Both `conseil` and `lorre` write to `syslog`. The logs are verbose enough to determine the point of synchronization between the database, the blockchain, and the status of lorre/conseil.  If any issues arise, please check the log to see whether the services are running and whether the chain and db are synced as this would be the starting point of all troubleshooting inquiries.

### Configuration

`conseil` and `lorre` use [HOCON](https://github.com/lightbend/config/blob/master/HOCON.md) format to store configuration. Read more at [Lightbend Config](https://github.com/lightbend/config) github page.

### Metadata overrides

Metadata enables dynamic querying of data sources provided by Conseil and allows for chain-agnostic UIs, like Arronax, to be built while still providing a high level of specialization. Some of the metadata override keys are helpful for query construction, but a large number of them exist for rendering.

In addition to the metadata derived from the schema, it's possible to extend and override it. This information is stored in configuration files inside `/src/main/resources/metadata/` which are referenced from `/src/main/resources/metadata.conf` that is itself included from `src/main/resources/reference.conf`. Metadata hierarchy is platform/network/entity/attribute, different options are available at each level.

Note the naming convention in this file. Hyphenated keys become camel-cased when presented via the metadata service in JSON format. For example `display-name` in the config file appears as `displayName` in the JSON response.

#### Common metadata

These overrides are available at all levels.

- `description`: Item description.
- `display-name`: Item name.
- `visible`: Item visibility, default is `false`. This means when a new entity is added to the Conseil database, it must be explicitly enabled.

#### Entity metadata

- `display-name-plural`: Arronax uses this to render entity tab titles here it will show "Blocks" instead of "Block".

#### Attribute metadata

- `currency-symbol`: Associated with Currency type fields, contains currency symbol or code, for example, 'XTZ', or 'ꜩ'
- `currency-symbol-code`: Same as `currency-symbol`, but stores the Unicode code point of the currency symbol, for 'ꜩ' it would be `42793`
- `data-format`: Presently used to customize date format. String contents of this item have no impact on how the data is sent or queried, it would be up to the implementing UI to interpret this content.
- `display-order`: Hints to a UI as to the order in which the fields should appear by default. Lower ordered items would be presented first.
- `display-priority`: Hints to the UI the relative priority of the data, allowing lower priority information that could be removed due to space constraints while keeping more relevant things in view. In contrast to order, lower priority items would be dropped off the view first.
- `data-type`: One of: DateTime, String, Int, Decimal, Boolean, Hash, AccountAddress, Currency.
- `placeholder`: Intended to be used for sample values that might appear as placeholder text in search boxes.
- `reference`: Associates the field with an attribute of another entity. This is effectively a foreign key reference.
- `scale`: Used with Decimal and Currency types to scale the value for display. This number is an `int` power of 10.


### Initializing the Data Store

Prior to starting the Conseil services for the first time the database initialization script needs to be run as such:

```
env SBT_OPTS="-Dconfig.file=conseil.conf" && sbt "genSchema"
```

## REST API Overview &amp; Examples

If you're already running a Conseil instance, there is an OpenAPI route at `<conseil-host>/docs`.

### Metadata Discovery

Conseil provides a dynamic query API. This means that newly exposed datasets can be automatically served through the interface. The metadata routes have a prefix of `/v2/metadata/`. All results are in JSON format.

#### Platforms

To get a list of platforms, make the following call.

```bash
curl --request GET --header 'apiKey: <API key>' --url '<conseil-host>/v2/metadata/platforms'
```

The result may look like the following.

```json
[{
    "name": "tezos",
    "displayName": "Tezos"
}]
```

#### Networks

To get a list of networks, take one of the platform `name` attributes from the call above to construct the following URL.

```bash
curl --request GET --header 'apiKey: <API key>' --url '<conseil-host>/v2/metadata/tezos/networks'
```

On a development server the result might be this:

```json
[{
    "name": "zeronet",
    "displayName": "Zeronet",
    "platform": "tezos",
    "network": "zeronet"
}, {
    "name": "alphanet",
    "displayName": "Alphanet",
    "platform": "tezos",
    "network": "alphanet"
}]
```

#### Entities

Taking again the `name` property from one of the network results, we can list the entities available for that network.

```bash
curl --request GET --header 'apiKey: <API key>' --url '<conseil-host>/v2/metadata/tezos/alphanet/entities'
```

With the following sample result.

```json
[{
    "name": "accounts",
    "displayName": "Accounts",
    "count": 19587
}, {
    "name": "accounts_checkpoint",
    "displayName": "Accounts checkpoint",
    "count": 5
}, {
    "name": "bakers",
    "displayName": "Bakers",
    "count": 9232185
}, {
    "name": "balance_updates",
    "displayName": "Balance updates",
    "count": 8848857
}, {
    "name": "ballots",
    "displayName": "Ballots",
    "count": 0
}, {
    "name": "blocks",
    "displayName": "Blocks",
    "count": 346319
}, {
    "name": "fees",
    "displayName": "Fees",
    "count": 1680
}, {
    "name": "operation_groups",
    "displayName": "Operation groups",
    "count": 2590969
}, {
    "name": "operations",
    "displayName": "Operations",
    "count": 2619443
}, {
    "name": "proposals",
    "displayName": "Proposals",
    "count": 0
}]
```

The `count` property above contains the number of records for a particular entity.

Keep in mind that not all of the entities provided in that list map directly to on-chain entities on Tezos alphanet. Conseil may provide custom pre-processed data for convenience. In this example the `fees` is one such entity that contains moving averages for operation fees in Tezos.

#### Attributes

Using the results from the `entities` call, we can get details of their composition.

```bash
curl --request GET --header 'apiKey: hooman' --url '<conseil-host>/v2/metadata/tezos/alphanet/accounts/attributes'
```

```json
[{
    "name": "account_id",
    "displayName": "Account id",
    "dataType": "String",
    "cardinality": 19587,
    "keyType": "UniqueKey",
    "entity": "accounts"
}, {
    "name": "block_id",
    "displayName": "Block id",
    "dataType": "String",
    "cardinality": 4614,
    "keyType": "NonKey",
    "entity": "accounts"
}, {
    "name": "manager",
    "displayName": "Manager",
    "dataType": "String",
    "cardinality": 15572,
    "keyType": "UniqueKey",
    "entity": "accounts"
}, {
    "name": "spendable",
    "displayName": "Spendable",
    "dataType": "Boolean",
    "cardinality": 2,
    "keyType": "NonKey",
    "entity": "accounts"
}, {
    "name": "delegate_setable",
    "displayName": "Delegate setable",
    "dataType": "Boolean",
    "cardinality": 2,
    "keyType": "NonKey",
    "entity": "accounts"
}, {
    "name": "delegate_value",
    "displayName": "Delegate value",
    "dataType": "String",
    "cardinality": 143,
    "keyType": "NonKey",
    "entity": "accounts"
}, {
    "name": "counter",
    "displayName": "Counter",
    "dataType": "Int",
    "keyType": "NonKey",
    "entity": "accounts"
}, {
    "name": "script",
    "displayName": "Script",
    "dataType": "String",
    "cardinality": 1317,
    "keyType": "NonKey",
    "entity": "accounts"
}, {
    "name": "balance",
    "displayName": "Balance",
    "dataType": "Decimal",
    "keyType": "NonKey",
    "entity": "accounts"
}, {
    "name": "block_level",
    "displayName": "Block level",
    "dataType": "Decimal",
    "keyType": "UniqueKey",
    "entity": "accounts"
}]
```

Using the information above it is possible to make intelligent decisions about how to construct user interfaces. For example, [Arronax](https://github.com/Cryptonomic/Arronax) creates drop-down lists for value selection for low-cardinality properties like `accounts.spendable`. Furthermore the `datatype` field allows display of the appropriate editor be it numeric or date entry. You'll note also that certain properties do no contain `cardinality`. Those are considered high cardinality fields for which that metadata is not collected.

#### Attribute Values

Finally for low-cardinality fields it is possible to get a list of distinct values to populate the drop-down lists mentioned above for example.

```bash
curl --request GET --header 'apiKey: <API key>' --url '<conseil-host>/v2/metadata/tezos/alphanet/operations/kind'
```

These are the operation types this particular Conseil instance has seen since start.

```json
["seed_nonce_revelation", "delegation", "transaction", "activate_account", "origination", "reveal", "double_endorsement_evidence", "double_baking_evidence", "endorsement"]
```

#### Attribute Values with Prefix

It is possible to get values for high-cardinality properties with a prefix. In this example we get all `accounts.manager` values starting with "KT1".

```bash
curl --request GET --header 'apiKey: <API key>' --url 'https://conseil-dev.cryptonomic-infra.tech:443/v2/metadata/tezos/alphanet/accounts/manager/KT1'
```

```json
...
```

#### Extended metadata

As mentioned in the [Datasource configuration](#datasource-configuration) section, it's possible to provide additional details for the items Conseil manages.

### Tezos Chain Data Query

Data requests in Conseil v2 API are sent via `POST` command and have a prefix of `/v2/data/<platform>/<network>/<entity>`. The items in angle braces match up to the `name` property of the related metadata results. There additional header to be set in the request: `Content-Type: application/json`.

The most basic query has the following JSON structure.

```json
{
    "fields": [],
    "predicates": [],
    "limit": 5
}
```

This will return five items without any filtering for some entity. Note that the query does not specify which entity is being requested. If this is sent to `/v2/data/tezos/alphanet/blocks`, it will return five rows with all the fields available in the blocks entity.

```bash
curl -d '{ "fields": [], "predicates": [], "limit": 5 }' -H 'Content-Type: application/json' -H 'apiKey: <API key>' -X POST '<conseil-host>/v2/data/tezos/alphanet/blocks/'
```

The query grammar allows for some fine-grained data extraction.

#### `fields`

It is possible to narrow down the fields returned in the result set by listing just the ones that are necessary. The `fields` property of the query is an array of `string`, these values are the `name`s from the `/v2/metadata/<platform>/<network>/<entity>/attributes/` metadata response.

#### `predicates`

`predicates` is an array containing objects. The inner object has several properties:
- `field` – attribute name from the metadata response.
- `operation` – one of: in, between, like, lt, gt, eq, startsWith, endsWith, before, after.
- `set` – an array of values to compare against. 'in' requires two or more elements, 'between' must have exactly two, the rest of the operations require a single element. Dates are expressed as epoch milliseconds.
- `inverse` – boolean, setting it to `true` applied a `NOT` to the operator
- `precision` – for numeric field matches this specifies the number of decimal places to round to.
- `group` - an optional naming to collect predicates in groups that will be eventually OR-ed amongst each other

#### `limit`

Specifies the maximum number of records to return.

#### `orderBy`

Sort condition, multiple may be supplied for a single query. Inner object(s) will contain `field` and `direction` properties. The former is `name` from the `/v2/metadata/<platform>/<network>/<entity>/attributes/` metadata response, the latter is one of 'asc', 'desc'.

It is possible to sort on aggregated results, in this case the `field` value would be "function_fieldname", for example `sum_balance`. See query samples below.

#### `aggregation`

It is possible to apply an aggregation function to a field of the result set. The aggregation object contains the following properties:

- `field` – `name` from the `/v2/metadata/<platform>/<network>/<entity>/attributes/` metadata response.
- `function` – one of: sum, count, max, min, avg.
- `predicate` – same definition as the predicate object described above. This gets translated into a `HAVING` condition in the underlying SQL.

Multiple fields can be aggregated and the same field can be aggregated with different functions in the same request. See examples below.

#### `output`

Default result set format is JSON, it is possible however to output csv instead for exporting larger datasets that will then be imported into other tools.

`'output': 'csv|json'`

### Business Intelligence &amp; Analytics

Here's a collection of interesting datasets. Run these examples as

```bash
curl -H 'Content-Type: application/json' -H 'apiKey: <API key>' \
-X POST '<conseil-host>/v2/data/<platform>/<network>/<entity>/' \
-d '<example>'
```

#### Bakers by block count in April 2019

Send this query to `/v2/data/tezos/<network>/blocks` Note that `orderBy` below ends up sorting by the aggregated value.

```json
{
    "fields": ["baker", "level"],
    "predicates": [{ "field": "timestamp", "set": [1554076800000, 1556668799000], "operation": "between", "inverse": false }],
    "orderBy": [{ "field": "count_level", "direction": "desc" }],
    "aggregation": [{ "field": "level", "function": "count" }],
    "limit": 50,
    "output": "csv"
}
```

#### Top 50 Bakers by delegator balance

Send this query to `/v2/data/tezos/<network>/accounts`

```json
{
    "fields": ["delegate_value", "balance"],
    "predicates": [{ "field": "delegate_value", "set": [], "operation": "isnull", "inverse": true }],
    "orderBy": [{ "field": "sum_balance", "direction": "desc" }],
    "aggregation": [{ "field": "balance", "function": "sum" }],
    "limit": 50,
    "output": "csv"
}
```

#### Top 50 Bakers by delegator count

Send this query to `/v2/data/tezos/<network>/accounts`

```json
{
    "fields": ["delegate_value", "account_id"],
    "predicates": [{ "field": "delegate_value", "set": [], "operation": "isnull", "inverse": true }],
    "orderBy": [{ "field": "count_account_id", "direction": "desc" }],
    "aggregation": [{ "field": "account_id", "function": "count" }],
    "limit": 50,
    "output": "csv"
}
```

#### Top 20 Bakers by roll count

Send this query to `/v2/data/tezos/<network>/rolls`

```json
{
    "fields": ["pkh", "rolls"],
    "predicates": [],
    "orderBy": [
        { "field": "block_level", "direction": "desc" },
        { "field": "rolls", "direction": "desc" }
    ],
    "limit": 20,
    "output": "csv"
}
```

#### All originated accounts which are smart contracts

Send this query to `/v2/data/tezos/<network>/accounts`

```json
{
    "fields": ["account_id"],
    "predicates": [
        { "field": "account_id", "set": ["KT1"], "operation": "startsWith", "inverse": false },
        { "field": "script", "set": [], "operation": "isnull", "inverse": true }
    ],
    "limit": 10000,
    "output": "csv"
}
```

#### Top 10 most active contracts

Send this query to `/v2/data/tezos/<network>/operations`

```json
{
    "fields": ["destination", "operation_group_hash"],
    "predicates": [
        { "field": "kind", "set": ["transaction"], "operation": "eq", "inverse": false },
        { "field": "destination", "set": ["KT1"], "operation": "startsWith", "inverse": false },
        { "field": "parameters", "set": [], "operation": "isnull", "inverse": true }
    ],
    "orderBy": [{ "field": "count_operation_group_hash", "direction": "desc" }],
    "aggregation": [{ "field": "operation_group_hash", "function": "count" }],
    "limit": 10,
    "output": "csv"
}
```

#### Top 10 contract originators

Send this query to `/v2/data/tezos/<network>/operations`

```json
{
    "fields": ["source", "operation_group_hash"],
    "predicates": [
        { "field": "kind", "set": ["origination"], "operation": "eq", "inverse": false },
        { "field": "script", "set": [], "operation": "isnull", "inverse": true }
    ],
    "orderBy": [{ "field": "count_operation_group_hash", "direction": "desc" }],
    "aggregation": [{ "field": "operation_group_hash", "function": "count" }],
    "limit": 10,
    "output": "csv"
}
```

#### Top 100 transactions in 2019

Send this query to `/v2/data/tezos/<network>/operations`

```json
{
    "fields": ["source", "amount", "fee"],
    "predicates": [
        { "field": "kind", "set": ["transaction"], "operation": "eq", "inverse": false },
        { "field": "timestamp", "set": [1546300800000, 1577836799000], "operation": "between", "inverse": false }
    ],
    "orderBy": [{ "field": "sum_amount", "direction": "desc" }],
    "aggregation": [{ "field": "amount", "function": "sum" }, { "field": "fee", "function": "avg" }
    ],
    "limit": 50,
    "output": "csv"
}
```

#### Fees by block level for transaction operations in April 2019

Send this query to `/v2/data/tezos/<network>/operations`

```json
{
    "fields": ["block_level", "kind", "fee"],
    "predicates": [
        { "field": "timestamp", "set": [1554076800000, 1556668799000], "operation": "between", "inverse": false },
        { "field": "fee", "set": [0], "operation": "gt", "inverse": false }
    ],
    "orderBy": [{ "field": "sum_fee", "direction": "desc" }],
    "aggregation": [{ "field": "fee", "function": "sum" }, { "field": "fee", "function": "avg" }],
    "limit": 100000,
    "output": "csv"
}
```

#### Number of transactions by type in April 2019

Send this query to `/v2/data/tezos/<network>/operations`

```json
{
    "fields": ["kind", "operation_group_hash"],
    "predicates": [
        { "field": "timestamp", "set": [1554076800000, 1556668799000], "operation": "between", "inverse": false }
    ],
    "orderBy": [{ "field": "count_operation_group_hash", "direction": "desc" }],
    "aggregation": [{ "field": "operation_group_hash", "function": "count" }],
    "limit": 20,
    "output": "csv"
}
```

#### Blocks by end-user transaction count in April 2019

Send this query to `/v2/data/tezos/<network>/operations`

```json
{
    "fields": ["block_level", "operation_group_hash"],
    "predicates": [
        { "field": "timestamp", "set": [1554076800000, 1556668799000], "operation": "between", "inverse": false },
        { "field": "kind", "set": ["transaction", "origination", "delegation", "activation", "reveal"], "operation": "in", "inverse": false }
    ],
    "orderBy": [{ "field": "count_operation_group_hash", "direction": "desc" }],
    "aggregation": [{ "field": "operation_group_hash", "function": "count" }],
    "limit": 100,
    "output": "csv"
}
```

#### Top 20 account controllers

Send this query to `/v2/data/tezos/<network>/accounts`

```json
{
    "fields": ["manager", "account_id"],
    "predicates": [
        { "field": "script", "set": [], "operation": "isnull", "inverse": false },
        { "field": "balance", "set": [0], "operation": "gt", "inverse": false }
    ],
    "orderBy": [{ "field": "count_account_id", "direction": "desc" }],
    "aggregation": [{ "field": "account_id", "function": "count" }],
    "limit": 20,
    "output": "csv"
}
```

#### Top 20 account controllers by balance

Send this query to `/v2/data/tezos/<network>/accounts`

```json
{
    "fields": ["manager", "balance"],
    "predicates": [
        { "field": "script", "set": [], "operation": "isnull", "inverse": false }
    ],
    "orderBy": [{ "field": "sum_balance", "direction": "desc" }],
    "aggregation": [{ "field": "balance", "function": "sum" }],
    "limit": 20,
    "output": "csv"
}
```

#### Blocks by end-user-transactions outside April 2019

Send this query to `/v2/data/tezos/<network>/operations`

```json
{
    "fields": ["block_level", "operation_group_hash"],
    "predicates": [
        { "field": "timestamp", "set": [1554076800000], "operation": "before", "inverse": false, "group": "before" },
        { "field": "kind", "set": ["transaction"], "operation": "in", "inverse": false, "group": "before" },
        { "field": "timestamp", "set": [1556668799000], "operation": "after", "inverse": false, "group": "after" },
        { "field": "kind", "set": ["transaction"], "operation": "in", "inverse": false, "group": "after" }
    ],
    "orderBy": [{ "field": "count_operation_group_hash", "direction": "desc" }],
    "aggregation": [{ "field": "operation_group_hash", "function": "count" }],
    "limit": 100,
    "output": "csv"
}
```

A note on predicate conjunction/disjunction

- By default all predicates with no specified group-name belong to the same un-named group
- predicates within the same group are joined via the "AND" operator
- predicates between separate groups are joing via the "OR" operator


### Preprocessed Data

Not all entities are necessarily items from the chain. One such example is `/v2/data/tezos/<network>/fees`. Conseil continuously buckets and averages network fees to give users an idea of what values they should submit when sending operations. Fees are aggregated by operation type.

```json
{
    "fields": [],
    "predicates": [{ "field": "kind", "operation": "eq", "set": [ "transaction" ] }],
    "orderBy": [{"field": "timestamp", "direction": "desc"}],
    "limit": 1
}
```
