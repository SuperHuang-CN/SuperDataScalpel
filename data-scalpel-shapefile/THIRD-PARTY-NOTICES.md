# Third-party notices

## spark-shp reference implementation

- Copyright 2019 Mansour Raad
- License: Apache License 2.0
- Local reference snapshot used during development: `spark-shp-master`
- License text: [Apache-2.0.txt](LICENSES/Apache-2.0.txt)

The reference project was used to understand component ordering and as the source of an optional Point compatibility fixture. The production implementation in this module independently re-expresses the public Esri Shapefile and dBASE binary formats with a different API and additional validation.

No Spark DataSource, RDD, Spark SQL, Hadoop filesystem, Scala, Esri Geometry API, geometry conversion or geometry-repair implementation is included. No external binary fixture is redistributed in this module.

The optional `ReferenceShapefileCompatibilityTest` accepts a local `.shp` path through the `shapefile.referenceFixture` system property and compares the reference Point fixture's schema, every attribute value, coordinates, record count, Charset and PRJ text.
