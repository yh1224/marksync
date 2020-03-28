# marksync

Synchronize Markdown documents to services.

Supported services are:

- [Qiita](https://qiita.com)
- [esa.io](https://esa.io)

## How to install

```bash
$ npm install -g marksync
```

### Setup

Create environment configuration file. (.marksync)

```shell
$ cp .marksync.example.qiita .marksync
```

## How to use

### Fetch all documents from service

You can fetch all documents from service.

```shell
$ marksync fetch -o <output>
```
### Write document

Create directory and index.md inside it, and execute

```bash
$ marksync new
```

### Check modified

```shell
$ marksync status
```

### Check differ

```shell
$ marksync diff
```

### Synchronize document

```shell
$ marksync update
```
