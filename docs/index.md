easy-preprocess-av-bag
=======================


Preprocesses a bag exported by easy-fedora-to-bag which contains AV materials.


SYNOPSIS
--------

    easy-preprocess-av-bag <bag-inbox> <bag-outbox>

DESCRIPTION
-----------

Preprocesses a bag exported by [easy-fedora-to-bag] which contains AV materials. The resulting bags are then further processed to deposits
by [easy-convert-bag-to-deposit] and ingested into a Data Station.

[easy-fedora-to-bag]: https://github.com/DANS-KNAW/easy-fedora-to-bag

[easy-convert-bag-to-deposit]: https://github.com/DANS-KNAW/easy-convert-bag-to-deposit

### Processing

The input is a directory containing bags exported from EASY by `easy-fedora-to-bag`. The output is a directory containing the preprocessed bags. Each input bag
results in two output bags, corresponding to two versions of the dataset to be created in the Data Station:

1. The original bag, with the files that were set to accessibility/visibility "NONE" *and* the pseudo files pointing to originals in the dark archive removed.
2. A second version of the dataset, with the streaming copies of the audio and video files added.

Where to find the streaming copies is specified in the configuration file:

```yaml
pseudoFileSources:
  springfieldDir: # ...location of directory containing files from Springfield (streaming copies)
  path: # ...location of a CSV file detailing where to find the files  


```

The CSV file should have the following format:

| easy-dataset-id    | path-in-springfield-dir | 
|--------------------|-------------------------|
| easy-dataset:67890 | path/to/file2           |
| easy-dataset:67890 | path/to/file4           |

INSTALLATION AND CONFIGURATION
------------------------------
Currently, this project is built as an RPM package for RHEL8 and later. The RPM will install the binaries to
`/opt/dans.knaw.nl/easy-preprocess-av-bag` and the configuration files to `/etc/opt/dans.knaw.nl/easy-preprocess-av-bag`.

For installation on systems that do no support RPM and/or systemd:

1. Build the tarball (see next section).
2. Extract it to some location on your system, for example `/opt/dans.knaw.nl/easy-preprocess-av-bag`.

BUILDING FROM SOURCE
--------------------
Prerequisites:

* Java 17 or higher
* Maven 3.3.3 or higher
* RPM

Steps:

    git clone https://github.com/DANS-KNAW/easy-preprocess-av-bag.git
    cd easy-preprocess-av-bag 
    mvn clean install

If the `rpm` executable is found at `/usr/local/bin/rpm`, the build profile that includes the RPM
packaging will be activated. If `rpm` is available, but at a different path, then activate it by using
Maven's `-P` switch: `mvn -Pprm install`.

Alternatively, to build the tarball execute:

    mvn clean install assembly:single
