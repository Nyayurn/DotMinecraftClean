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

package com.github.anmsakura.minecraftclean;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 清理.minecraft文件夹下的无用资源和依赖库(assets和libraries文件夹)
 *
 * @author ANMSakura
 */
public class Main {
    public static void main(String... args) {
        // FlatAtomOneDarkIJTheme.setup();
        String dotMinecraftPath = args[0].trim();
        String versionsPath = dotMinecraftPath + FileUtil.FILE_SEPARATOR + "versions";
        String assetsPath = dotMinecraftPath + FileUtil.FILE_SEPARATOR + "assets";
        String librariesPath = dotMinecraftPath + FileUtil.FILE_SEPARATOR + "libraries";
        List<File> dependenciesAssets = new ArrayList<>();
        List<File> dependenciesLibraries = new ArrayList<>();

        // 遍历版本文件夹
        if (FileUtil.exist(versionsPath)) {
            Arrays.stream(FileUtil.ls(versionsPath)).filter(FileUtil::isDirectory).forEach(versionFolder -> {
                // 读取版本.json
                JSONObject versionsJson = JSONUtil.readJSONObject(
                        new File(versionFolder, versionFolder.getName() + ".json"), StandardCharsets.UTF_8);
                // 版本json文件内的assetsIndex
                JSONObject assetIndexJson = versionsJson.getJSONObject("assetIndex");
                // 获取assetsIndex.json文件
                File assetsIndexFile = new File(assetsPath,
                        String.format("indexes/%s.json", assetIndexJson.getStr("id")));

                // 添加资源
                if (FileUtil.exist(assetsIndexFile)) {
                    // 添加assetsIndex.json
                    dependenciesAssets.add(assetsIndexFile);
                    // 读取assetsIndex.json
                    JSONObject assetsIndexJson = JSONUtil.readJSONObject(assetsIndexFile,
                            StandardCharsets.UTF_8);
                    // 获取assetsIndex.json的objects
                    JSONObject objectsJson = assetsIndexJson.getJSONObject("objects");

                    // 获取并添加被依赖的assets文件
                    objectsJson.keySet().forEach(key -> {
                        String hash = objectsJson.getByPath(key + ".hash", String.class);
                        File assetFile = new File(assetsPath,
                                String.format("objects/%s/%s", hash.substring(0, 2), hash));
                        dependenciesAssets.add(assetFile);
                    });
                }

                // 版本json文件内的libraries
                JSONArray librariesJsonArray = versionsJson.getJSONArray("libraries");

                // 添加依赖库
                librariesJsonArray.jsonIter().forEach(libraryJson -> {
                    List<File> libraryFile = new ArrayList<>();
                    if (!libraryJson.isNull("downloads")) {
                        JSONObject downloadsJson = libraryJson.getJSONObject("downloads");
                        if (!downloadsJson.isNull("artifact")) {
                            // 常规
                            JSONObject artifactJson = downloadsJson.getJSONObject("artifact");
                            String path = artifactJson.getStr("path");
                            libraryFile.add(new File(librariesPath, path));
                        } else if (!downloadsJson.isNull("classifiers")) {
                            // Lwjgl
                            JSONObject classifiersJson = downloadsJson.getJSONObject("classifiers");
                            classifiersJson.keySet().forEach(key -> {
                                JSONObject nativeJson = classifiersJson.getJSONObject(key);
                                String path = nativeJson.getStr("path");
                                libraryFile.add(new File(librariesPath, path));
                            });
                        }
                    } else if (!libraryJson.isNull("url")) {
                        // Fabric
                        String[] parts = libraryJson.getStr("name").split(":");
                        String path = String.format("%s/%s-%s.jar",
                                String.join("/", parts[0].replaceAll("\\.", "/"), parts[1],
                                        parts[2]), parts[1], parts[2]);
                        libraryFile.add(new File(librariesPath, path));
                    }
                    if (libraryFile.isEmpty()) {
                        System.out.println(libraryJson);
                    } else {
                        dependenciesLibraries.addAll(libraryFile);
                    }
                });
            });
        }

        // 删除Assets
        if (FileUtil.exist(assetsPath)) {
            deleteFile(assetsPath, dependenciesAssets);
        }

        // 删除Libraries
        if (FileUtil.exist(librariesPath)) {
            deleteFile(librariesPath, dependenciesLibraries);
        }
    }

    private static void deleteFile(String path, List<File> usefulFiles) {
        // 删除无用文件
        FileUtil.loopFiles(path).stream().filter(FileUtil::isFile).forEach(f -> {
            if (!usefulFiles.contains(new File(path))) {
                FileUtil.del(f);
            }
        });

        // 清理空文件夹
        FileUtil.cleanEmpty(new File(path));
    }
}
