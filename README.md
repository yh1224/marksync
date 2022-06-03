# marksync

[![npm version](https://badge.fury.io/js/marksync.png)](https://badge.fury.io/js/marksync)

Synchronize Markdown documents to services.

Supported services are:

- [Qiita](https://qiita.com)
- [esa.io](https://esa.io)
- [Zenn](https://zenn.dev) (Experimental)

## Install

```bash
$ npm install -g marksync
```

### Setup

Create environment configuration file (.marksync) in top of documents directory structure.

#### Qiita

```
# Qiita
SERVICE=qiita
QIITA_USERNAME=
QIITA_ACCESS_TOKEN=

# S3 uploader
#UPLOADER=s3
#S3_BUCKET_NAME=
#S3_PREFIX=
#S3_BASE_URL=
#AWS_PROFILE=
#AWS_ACCESS_KEY_ID=
#AWS_SECRET_ACCESS_KEY=
```

#### esa.io

```
# esa
SERVICE=esa
ESA_TEAM=
ESA_USERNAME=
ESA_ACCESS_TOKEN=
```

## Usage

### Import

To import all documents from the service, run

```shell
$ marksync import -o <output>
```

### New document

Create a directory and document file (index.md) inside it, and run

```bash
$ marksync new
```

This will create the meta file (marksync.xxxx.yaml).

### Check modified

To check if there are differences between the local file and document on the service, run

```shell
$ marksync status
```

### Check differ

To print differences between the local file and document on the service, run

```shell
$ marksync diff
```

### Update

```shell
$ marksync update
```

This will update the document on the service by the local file.
