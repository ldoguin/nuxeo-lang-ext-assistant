/*
 * Copyright (c) 2006-2012 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * ldoguin
 * 
 */
package org.nuxeo.lang.ext;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.lang.LocaleUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.CheckoutConflictException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRefNameException;
import org.eclipse.jgit.api.errors.RefAlreadyExistsException;
import org.eclipse.jgit.api.errors.RefNotFoundException;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.dircache.DirCacheIterator;
import org.eclipse.jgit.errors.CorruptObjectException;
import org.eclipse.jgit.errors.NoWorkTreeException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.FileTreeIterator;
import org.eclipse.jgit.treewalk.filter.PathFilterGroup;
import org.jboss.seam.log.LogProvider;
import org.jboss.seam.log.Logging;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.nuxeo.common.Environment;
import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.Blob;
import org.nuxeo.ecm.webengine.forms.FormData;
import org.nuxeo.ecm.webengine.model.Template;
import org.nuxeo.ecm.webengine.model.WebObject;
import org.nuxeo.ecm.webengine.model.impl.ModuleRoot;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.reload.ReloadService;

/**
 * The root entry for the WebEngine module.
 * 
 * @author ldoguin
 */
@Path("/langExtAssistantRoot")
@Produces("text/html;charset=UTF-8")
@WebObject(type = "LangExtAssistantRoot")
public class LangExtAssistantRoot extends ModuleRoot {

    private static final LogProvider log = Logging.getLogProvider(LangExtAssistantRoot.class);

    private static final String ORIGINAL_FILE_EXTENSION = ".original";

    private static final String MESSAGES_FILENAME = "messages%s.properties";

    private static final List<String> nuxeoManagedLanguages = Arrays.asList(
            "en", "default", "fr");

    private static final Pattern messagesPattern = Pattern.compile(
            "(messages_)(.*)(.properties)", Pattern.CASE_INSENSITIVE
                    | Pattern.DOTALL);

    private static final Pattern localPattern = Pattern.compile(
            "[a-zA-Z]{2}|[a-zA-Z]{2}_[a-zA-Z]{2}", Pattern.CASE_INSENSITIVE
                    | Pattern.DOTALL);

    private static final String CLASSES_FOLDER_PATH = "src/main/resources/web/nuxeo.war/WEB-INF/classes/";

    private static final String LOCAL_PATH = Environment.getDefault().getData().getAbsolutePath()
            + "/nuxeo-platform-lang-ext/";

    private static final String ABSOLUTE_GIT_PATH_PATTERN = LOCAL_PATH
            + CLASSES_FOLDER_PATH + MESSAGES_FILENAME;

    private static final String GIT_REPO_REMOTE_PATH = "git://github.com/nuxeo/nuxeo-platform-lang-ext.git";

    private static final Properties mergedProp = new Properties();

    private static final List<String> sortedKeys = new ArrayList<String>();

    private static final Map<String, Properties> locales = new HashMap<String, Properties>();

    private static Repository localRepo;

    private static Git git;

    @Override
    protected void initialize(Object... args) {
        super.initialize(args);
        if (mergedProp.isEmpty()) {
            try {
                File localGitRepo = new File(LOCAL_PATH);
                if (!localGitRepo.exists()) {
                    Git.cloneRepository().setURI(GIT_REPO_REMOTE_PATH).setDirectory(
                            localGitRepo).call();
                }
                git = Git.open(localGitRepo);
                localRepo = git.getRepository();
                Properties enProp = loadProperties("en",
                        String.format(MESSAGES_FILENAME, "_en"));
                Properties defaultProp = loadProperties("default",
                        String.format(MESSAGES_FILENAME, ""));
                mergedProp.putAll(defaultProp);
                mergedProp.putAll(enProp);
                Enumeration<Object> keys = mergedProp.keys();
                while (keys.hasMoreElements()) {
                    sortedKeys.add((String) keys.nextElement());
                }
                Collections.sort(sortedKeys);
                URL classesUrl = getClass().getResource("/");
                File f = new File(classesUrl.toURI());
                String[] messagesFiles = f.list();
                for (String messagesFile : messagesFiles) {
                    Matcher m = messagesPattern.matcher(messagesFile);
                    if (m.matches()) {
                        String languageKey = m.group(2);
                        loadProperties(languageKey, messagesFile);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(
                        "could not initialize webengine module " + this, e);
            }
        }
    }

    @GET
    @Path("reset")
    public Object reset() throws RefAlreadyExistsException,
            RefNotFoundException, InvalidRefNameException,
            CheckoutConflictException, GitAPIException {
        mergedProp.clear();
        sortedKeys.clear();
        locales.clear();
        if (git != null) {
            git.checkout().setAllPaths(true).call();
            git = null;
            localRepo = null;
        }
        initialize();
        return Response.ok().build();
    }

    @GET
    public Object doGet() {
        Set<String> localesKeySet = locales.keySet();
        // remove language manage by nuxeo
        localesKeySet.removeAll(nuxeoManagedLanguages);
        List<Locale> localeList = new ArrayList<Locale>(localesKeySet.size());
        try {
            for (String key : localesKeySet) {
                Locale locale = LocaleUtils.toLocale(key);
                localeList.add(locale);
            }
            return getView("index").arg("availableLanguages", localeList);
        } catch (Exception e) {
            return Response.serverError().build();
        }
    }

    @GET
    @Path("lang/{languageKey}")
    public Object getLang(@PathParam("languageKey") String languageKey) {
        if (!isKeyValid(languageKey)) {
            return Response.status(404).build();
        } else {
            return getView("translationForm").arg("sortedKeys", sortedKeys).arg(
                    "defaultProperties", mergedProp).arg("languageProperties",
                    locales.get(languageKey)).arg("languageKey", languageKey);
        }
    }

    @POST
    @Path("upload")
    @Consumes({ MediaType.MULTIPART_FORM_DATA })
    public Object uploadMessageFile() throws IOException, JSONException {
        FormData form = ctx.getForm();
        Blob messageFile = form.getBlob("uploadedFile");
        String fileName = messageFile.getFilename();
        Matcher m = messagesPattern.matcher(fileName);
        if (m.matches()) {
            String languageKey = m.group(2);
            if (nuxeoManagedLanguages.contains(languageKey)) {
                Template template = (Template) doGet();
                return template.arg("error_message",
                        "You cannot update file for default locale like en or fr.");
            }
            // Verify if the locale is supported by the server
            try {
                Locale locale = LocaleUtils.toLocale(languageKey);
            } catch (Exception e) {
                Template template = (Template) doGet();
                return template.arg("error_message",
                        "Could not identify the locale.");
            }
            Properties properties = new Properties();
            properties.load(messageFile.getStream());
            Properties existingProperties = locales.get(languageKey);
            if (existingProperties != null) {
                // merge uploaded properties with the old one
                Properties finalProperties = new Properties();
                finalProperties.putAll(existingProperties);
                finalProperties.putAll(properties);
                locales.put(languageKey, finalProperties);
            } else {
                locales.put(languageKey, properties);
            }
            return doGet();
        } else {
            Template template = (Template) doGet();
            return template.arg("error_message",
                    "Given file name was not like messages_LANG.properties");
        }
    }

    @PUT
    @Path("lang/{languageKey}/update")
    @Consumes({ MediaType.APPLICATION_JSON })
    public Object update(@PathParam("languageKey") String languageKey)
            throws IOException, JSONException {
        if (!isKeyValid(languageKey)
                || nuxeoManagedLanguages.contains(languageKey)) {
            return Response.status(404).build();
        } else {
            Properties props = locales.get(languageKey);
            if (props == null) {
                return Response.serverError().build();
            }
            String content = new java.util.Scanner(
                    this.request.getInputStream()).useDelimiter("\\A").next();
            JSONArray modifiedFields = new JSONArray(content);
            for (int i = 0; i < modifiedFields.length(); i++) {
                JSONObject field = (JSONObject) modifiedFields.get(i);
                props.put(field.getString("id"), field.getString("value"));
            }
            locales.put(languageKey, props);
            persistMessages(languageKey);
            reloadMessages();
            return "";
        }
    }

    @GET
    @Path("lang/{languageKey}/file")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getLangFile(@PathParam("languageKey") String languageKey)
            throws FileNotFoundException, IOException {
        if (!isKeyValid(languageKey)) {
            return Response.status(404).build();
        } else {
            if (locales.get(languageKey) != null) {

                String filePath = getGitRepoAbsolutePath("_" + languageKey);
                File f = new File(filePath);
                ResponseBuilder response = Response.ok(f);
                response.type("text/plain");
                response.header("Content-Disposition",
                        "attachment; filename=\"" + f.getName() + "\"");
                return response.build();
            }
            return Response.status(Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("lang/{languageKey}/diff")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getDiff(@PathParam("languageKey") String languageKey)
            throws FileNotFoundException, IOException {
        if (!isKeyValid(languageKey)) {
            return Response.status(404).build();
        } else {
            if (locales.get(languageKey) != null) {
                addToRepo(languageKey);
                String diff = getRepoDiff(languageKey);
                String fileName = "messages_" + languageKey
                        + ".properties.diff";
                ResponseBuilder response = Response.ok(diff);
                response.type("text/plain");
                response.header("Content-Disposition",
                        "attachment; filename=\"" + fileName + "\"");
                return response.build();
            }
            return Response.status(Status.NOT_FOUND).build();
        }
    }

    @GET
    @Path("lang/{languageKey}/removeDuplicatedKeys")
    public Object removeDuplicate(@PathParam("languageKey") String languageKey)
            throws FileNotFoundException, IOException {
        if (!isKeyValid(languageKey)) {
            return Response.status(404).build();
        } else {
            if (locales.get(languageKey) != null) {
                removeDuplicateKeys(languageKey);
                return getLang(languageKey);
            }
            return Response.status(Status.NOT_FOUND).build();
        }
    }

    private String getRepoDiff(String languageKey) throws NoWorkTreeException,
            CorruptObjectException, IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String filePath = CLASSES_FOLDER_PATH + "messages_" + languageKey
                + ".properties";

        DiffFormatter df = new DiffFormatter(out);
        df.setRepository(localRepo);
        df.setPathFilter(PathFilterGroup.createFromStrings(filePath));
        DirCacheIterator oldTree = new DirCacheIterator(
                localRepo.readDirCache());

        FileTreeIterator newTree = new FileTreeIterator(localRepo);

        df.format(oldTree, newTree);
        df.flush();
        df.release();
        String diff = out.toString("utf-8");
        return diff;
    }

    public void addToRepo(String languageKey) throws IOException {
        String commitFile = getGitRepoAbsolutePath("_" + languageKey);
        File originalFile = new File(commitFile);
        File diffFile = File.createTempFile("messages", "properties");
        Properties props = locales.get(languageKey);
        if (!originalFile.exists()) {
            originalFile.createNewFile();
        }
        Scanner freader = new Scanner(originalFile);
        BufferedWriter writer = new BufferedWriter(new FileWriter(diffFile,
                false));

        String line = null;
        String key = null;
        String label = null;
        String propertiesLabel = null;
        String[] keyLabelPair = null;
        StringBuffer sb = null;
        String encodedPropertiesLabel;
        while (freader.hasNextLine()) {
            line = freader.nextLine();
            if (line.startsWith("#")) {
                // ignore comments
                writer.write(line);
                writer.newLine();
            } else {
                if (line.indexOf("=") == line.length() - 1 && line.length() > 2) {
                    key = line.substring(0, line.length() - 1);
                    if (props.get(key) != null && !"".equals(props.get(key))) {
                        propertiesLabel = (String) props.get(key);
                    } else {
                        propertiesLabel = null;
                    }
                    if (propertiesLabel != null) {
                        encodedPropertiesLabel = escapeUnicode(propertiesLabel);
                        sb = new StringBuffer();
                        sb.append(key);
                        sb.append("=");
                        sb.append(encodedPropertiesLabel);
                        writer.write(sb.toString());
                        writer.newLine();
                    } else {
                        // no difference
                        writer.write(line);
                        writer.newLine();
                    }

                } else {
                    keyLabelPair = line.split("=", 2);
                    if (keyLabelPair.length != 2) {
                        // Something's wrong
                        writer.write(line);
                        writer.newLine();
                    } else {
                        key = keyLabelPair[0];
                        label = keyLabelPair[1];
                        if (props.get(key) != null) {
                            propertiesLabel = (String) props.get(key);
                        } else {
                            propertiesLabel = null;
                        }
                        if (propertiesLabel != null) {
                            encodedPropertiesLabel = escapeUnicode(propertiesLabel);
                            if (!encodedPropertiesLabel.equalsIgnoreCase(label)) {
                                sb = new StringBuffer();
                                sb.append(key);
                                sb.append("=");
                                sb.append(encodedPropertiesLabel);
                                writer.write(sb.toString());
                                writer.newLine();
                            } else {
                                // no difference
                                writer.write(line);
                                writer.newLine();
                            }
                        } else {
                            // no difference
                            writer.write(line);
                            writer.newLine();
                        }
                    }
                }
            }
        }

        freader.close();
        writer.close();
        FileUtils.copy(diffFile, originalFile);
    }

    private void removeDuplicateKeys(String languageKey) throws IOException {
        String commitFile = getGitRepoAbsolutePath("_" + languageKey);
        File originalFile = new File(commitFile);
        File diffFile = File.createTempFile("messages", "properties");
        if (!originalFile.exists()) {
            originalFile.createNewFile();
        }
        Scanner freader = new Scanner(originalFile);

        Map<String, List<Integer>> lines = new HashMap<String, List<Integer>>();
        Integer lineIdx = 0;
        String line = null;
        String key = null;
        String[] keyLabelPair = null;
        while (freader.hasNextLine()) {
            lineIdx++;
            line = freader.nextLine();
            if (!line.startsWith("#")) {
                if (line.indexOf("=") == line.length() - 1 && line.length() > 2) {
                    key = line.substring(0, line.length() - 1);
                } else {
                    keyLabelPair = line.split("=", 2);
                    if (keyLabelPair.length == 2) {
                        key = keyLabelPair[0];
                    }
                }
            }
            if (key != null) {
                List<Integer> keyOccurences = lines.get(key);
                if (keyOccurences == null) {
                    keyOccurences = new ArrayList<Integer>();
                }
                keyOccurences.add(lineIdx);
                lines.put(key, keyOccurences);
            }
            key = null;
        }
        freader.close();

        freader = new Scanner(originalFile);
        BufferedWriter writer = new BufferedWriter(new FileWriter(diffFile,
                false));
        lineIdx = 0;
        while (freader.hasNextLine()) {
            lineIdx++;
            line = freader.nextLine();
            if (!line.startsWith("#")) {
                if (line.indexOf("=") == line.length() - 1 && line.length() > 2) {
                    key = line.substring(0, line.length() - 1);
                } else {
                    keyLabelPair = line.split("=", 2);
                    if (keyLabelPair.length == 2) {
                        key = keyLabelPair[0];
                    }
                }
            }
            if (key != null) {
                List<Integer> keyOccurences = lines.get(key);
                if (keyOccurences.size() > 1) {
                    keyOccurences.remove(lineIdx);
                    lines.put(key, keyOccurences);
                } else {
                    // no difference
                    writer.write(line);
                    writer.newLine();
                }
            } else {
                writer.write(line);
                writer.newLine();
            }
            key = null;
        }
        freader.close();
        writer.close();
        FileUtils.copy(diffFile, originalFile);
    }

    public String escapeUnicode(String input) {
        StringBuilder b = new StringBuilder(input.length());
        Formatter f = new Formatter(b);
        for (char c : input.toCharArray()) {
            if (c < 128) {
                b.append(c);
            } else {
                f.format("\\u%04x", (int) c);
            }
        }
        return b.toString();
    }

    private Properties loadProperties(String key, String filePath) {
        if (locales.get(key) == null) {
            Properties properties = new Properties();
            InputStream is = getClass().getResourceAsStream("/" + filePath);
            if (null != is) {
                try {
                    properties.load(is);
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe);
                }
            }
            locales.put(key, properties);
        }
        return locales.get(key);
    }

    private void persistMessages(String languageKey)
            throws FileNotFoundException, IOException {
        Properties messages = locales.get(languageKey);
        if (messages != null) {
            String fileName = String.format(MESSAGES_FILENAME, "_"
                    + languageKey);
            URL url = getClass().getResource("/" + fileName);
            File f = new File(url.getFile());
            if (f.exists()) {
                backupOriginalFile(f);
                // Overwrite existing file
                Properties props = locales.get(languageKey);
                props.store(new FileOutputStream(f), null);
            }
        }
    }

    private String getGitRepoAbsolutePath(String languageKey) {
        return String.format(ABSOLUTE_GIT_PATH_PATTERN, languageKey);

    }

    private void backupOriginalFile(File f) throws IOException {
        String filePath = f.getAbsolutePath();
        String originalFilePath = filePath.concat(ORIGINAL_FILE_EXTENSION);
        File originalFile = new File(originalFilePath);
        if (!originalFile.exists()) {
            // no exisiting backup
            FileUtils.copy(f, originalFile);
        }
    }

    private void reloadMessages() {
        if (Framework.isDevModeSet()) {
            ReloadService srv = Framework.getLocalService(ReloadService.class);
            try {
                srv.flush();
            } catch (Exception e) {
                log.error("Error while flushing the application in dev mode", e);
            }
        }
    }

    private boolean isKeyValid(String key) {
        Matcher m = localPattern.matcher(key);
        if (m.matches()) {
            return true;

        }
        return false;
    }
}
