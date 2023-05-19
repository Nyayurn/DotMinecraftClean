/**
 * Copyright 2023 ANMSakura
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package main.java.com.github.anmsakura.minecraftclean;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatAtomOneDarkIJTheme;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * 清理.minecraft文件夹下的无用资源和依赖库(assets和libraries文件夹)
 *
 * @author ANMSakura
 */
public class Main {

    public static void main(String... args) throws IOException {
        // 为GUI设置主题。
        FlatAtomOneDarkIJTheme.setup();
        // .minecraft
        File minecraftFolder = new File(args[0]);
        // .minecraft/versions
        File versionsFolder = new File(minecraftFolder, "versions");
        // .minecraft/assets
        File assetsFolder = new File(minecraftFolder, "assets");
        // .minecraft/libraries
        File librariesFolder = new File(minecraftFolder, "libraries");
        // 被依赖的文件
        List<File> usefulAssetFiles = new ArrayList<>();
        List<File> usefulLibraryFiles = new ArrayList<>();

        // 遍历版本文件夹
        for (File versionFolder : Objects.requireNonNull(versionsFolder.listFiles())) {
            // 读取版本.json
            JSONObject versionsJson = JSON.parseObject(Files.readAllBytes(new File(versionFolder, versionFolder.getName() + ".json").toPath()));
            // 版本json文件内的assetsIndex
            JSONObject assetIndexJson = versionsJson.getJSONObject("assetIndex");
            // 获取assetsIndex.json文件
            File assetsIndexFile = new File(assetsFolder, "indexes/" + assetIndexJson.getString("id") + ".json");

            // 添加资源
            if (assetsIndexFile.exists()) {
                // 添加assetsIndex.json
                usefulAssetFiles.add(assetsIndexFile);
                // 读取assetsIndex.json
                JSONObject assetsIndexJson = JSON.parseObject(Files.readAllBytes(assetsIndexFile.toPath()));
                // 获取assetsIndex.json的objects
                JSONObject objectsJson = assetsIndexJson.getJSONObject("objects");

                // 获取并添加被依赖的assets文件
                for (String key : objectsJson.keySet()) {
                    JSONObject objectObj = objectsJson.getJSONObject(key);
                    String hash = objectObj.getString("hash");
                    File assetFile = new File(assetsFolder, "objects/" + hash.substring(0, 2) + "/" + hash);
                    usefulAssetFiles.add(assetFile);
                }
            }

            // 版本json文件内的libraries
            JSONArray librariesJsonArray = versionsJson.getJSONArray("libraries");

            // 添加依赖库
            for (int i = 0; i < librariesJsonArray.size(); i++) {
                JSONObject libraryJson = librariesJsonArray.getJSONObject(i);
                List<File> libraryFile = new ArrayList<>();
                if (libraryJson.containsKey("downloads")) {
                    JSONObject downloadsJson = libraryJson.getJSONObject("downloads");
                    if (downloadsJson.containsKey("artifact")) {
                        // 常规
                        JSONObject artifactJson = downloadsJson.getJSONObject("artifact");
                        String path = artifactJson.getString("path");
                        libraryFile.add(new File(librariesFolder, path));
                    } else if (downloadsJson.containsKey("classifiers")) {
                        // Lwjgl
                        JSONObject classifiersJson = downloadsJson.getJSONObject("classifiers");
                        for (String key : classifiersJson.keySet()) {
                            JSONObject nativeJson = classifiersJson.getJSONObject(key);
                            String path = nativeJson.getString("path");
                            libraryFile.add(new File(librariesFolder, path));
                        }
                    }
                } else if (libraryJson.containsKey("url")) {
                    // Fabric
                    String[] parts = libraryJson.getString("name").split(":");
                    String path = String.format("%s/%s-%s.jar",
                            String.join("/", parts[0]
                                    .replaceAll("\\.", "/"), parts[1], parts[2]), parts[1], parts[2]);
                    libraryFile.add(new File(librariesFolder, path));
                }
                if (libraryFile.isEmpty()) {
                    System.out.println(libraryJson);
                    continue;
                }
                usefulLibraryFiles.addAll(libraryFile);
            }
        }

        // 删除文件
        for (File file : Objects.requireNonNull(assetsFolder.listFiles())) {
            deleteFile(file, usefulAssetFiles);
        }
        for (File file : Objects.requireNonNull(librariesFolder.listFiles())) {
            deleteFile(file, usefulLibraryFiles);
        }

    }

    private static void deleteFile(File file, List<File> usefulFiles) {
        if (file.isFile()) {
            // 如果未被依赖，则删除
            if (!usefulFiles.contains(file)) {
                boolean ignored = file.delete();
            }
        } else {
            // 如果是不空文件夹，则尝试删除文件夹内的所有文件
            if (!(Objects.requireNonNull(file.list()).length == 0)) {
                for (File listFile : Objects.requireNonNull(file.listFiles())) {
                    // 递龟
                    deleteFile(listFile, usefulFiles);
                }
            }
            // 如果是空文件夹，则删除
            if (Objects.requireNonNull(file.list()).length == 0) {
                boolean ignored = file.delete();
            }
        }
    }
}
