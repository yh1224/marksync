# marksync

Synchronize Markdown documents to services.

Supported services are:

- [Qiita](https://qiita.com)
- [esa](https://esa.io)

## Prerequisite

- [Scala SBT](https://www.scala-sbt.org/)

## How to use

### Setup

Place .env file.

```shell
$ cp .env.example.qiita .env
```

### Write

Place index.md and metadata(qiita.yml or esa-{teamName}.yml) into one folder.

See [example](example) folder.

### Check modified

```shell
$ marksync check <target>...
```

### Update

```shell
$ marksync update <target>...
```

## Fetch all documents from service

You can fetch documents from service.

```shell
$ marksync fetch -o <output>
```
