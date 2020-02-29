# marksync

Synchronize Markdown documents to services.

Supported services are:

- [Qiita](https://qiita.com)
- [esa](https://esa.io)

## How to use

### Setup

Place .marksync configuration file.

```shell
$ cp .marksync.example.qiita .marksync
```

### Write document

Place index.md and meta data (marksync.qiita.yml or marksync.esa-{teamName}.yml) into one folder. See [examples](examples) folder.

### Check modified

```shell
$ marksync check
```

### Update

```shell
$ marksync update
```

## Fetch all documents from service

You can fetch all documents from service.

```shell
$ marksync fetch -o <output>
```
