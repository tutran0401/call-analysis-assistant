package com.tutran.callassistant.application.port.out;

import java.nio.file.Path;
import java.util.List;

/** Liệt kê các thư mục cuộc gọi nằm dưới một thư mục gốc data mẫu. */
public interface CallDirectoryScanner {

    List<Path> callDirectoriesUnder(Path dataRoot);
}
