# Third-party notices

## FileGDB-master reference implementation

- Copyright 2018-present Mansour Raad
- License: Apache License 2.0
- Local reference snapshot used during development: `FileGDB-master`
- License text: [Apache-2.0.txt](LICENSES/Apache-2.0.txt)

The implementation in this module does not include the reference project's Spark, Hadoop or Scala API. The following binary-format behavior was independently re-expressed in Java with additional bounds checking and a different public API:

- `GDBIndex.scala`: 4/5/6-byte little-endian `.gdbtablx` offsets and deleted zero slots;
- `GDBTable.scala`: uncompressed table header and field descriptor ordering;
- `GDBTableIterator.scala` and `GDBField.scala`: nullable bitmap and scalar record order;
- `FieldXY*.scala`: quantized Point coordinates;
- `FieldMultiPoint*.scala`: MultiPoint envelope and delta-encoded XY/Z/M coordinates;
- `FieldMultiPart*.scala`: Polyline path and Polygon ring sizes with delta-encoded XY/Z/M coordinates;
- `package.scala`: FileGDB unsigned and signed variable integers.

No external `.gdb` fixture from the reference snapshot is redistributed by this module. Optional compatibility tests accept local fixture paths through Maven system properties.
