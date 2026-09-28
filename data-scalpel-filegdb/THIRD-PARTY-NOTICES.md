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

## GDAL OpenFileGDB format cross-checks

The following GDAL source files were consulted to cross-check binary-format behavior. The Java
implementation and deterministic tests were independently written; no GDAL code or native library
is bundled or required at runtime. GDAL-generated diagnostic datasets are not redistributed.

- [GDAL 3.9.3 field writer](https://github.com/OSGeo/gdal/blob/v3.9.3/ogr/ogrsf_frmts/openfilegdb/filegdbtable_write_fields.cpp): optional `DE AD BE EF` terminator, scalar default lengths and string encoding flags.
- [GDAL 3.9.3 table reader](https://github.com/OSGeo/gdal/blob/v3.9.3/ogr/ogrsf_frmts/openfilegdb/filegdbtable.cpp): sparse index block-map layout and UTF-16 text values.
- [GDAL 3.12.4 geometry constants](https://github.com/OSGeo/gdal/blob/v3.12.4/ogr/ogrpgeogeometry.h): ordinary, Z/M and general shape codes.
