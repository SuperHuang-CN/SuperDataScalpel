package cn.superhuang.data.scalpel.shapefile;

/** Fixed physical components that form one Shapefile dataset. */
public enum ShapefileComponent {
    SHP("shp", true),
    SHX("shx", true),
    DBF("dbf", true),
    CPG("cpg", false),
    PRJ("prj", false);

    private final String extension;
    private final boolean required;

    ShapefileComponent(String extension, boolean required) {
        this.extension = extension;
        this.required = required;
    }

    public String extension() {
        return extension;
    }

    public boolean required() {
        return required;
    }
}
