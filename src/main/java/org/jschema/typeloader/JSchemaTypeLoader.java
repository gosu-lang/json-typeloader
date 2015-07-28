package org.jschema.typeloader;

import gw.config.CommonServices;
import gw.fs.IDirectory;
import gw.fs.IFile;
import gw.lang.reflect.IType;
import gw.lang.reflect.RefreshKind;
import gw.lang.reflect.TypeLoaderBase;
import gw.lang.reflect.TypeSystem;
import gw.lang.reflect.module.IModule;
import gw.util.GosuExceptionUtil;
import gw.util.Pair;
import gw.util.concurrent.LockingLazyVar;
import org.jschema.model.JsonMap;
import org.jschema.parser.JSchemaParser;
import org.jschema.parser.JsonParseError;
import org.jschema.parser.JsonParseException;
import org.jschema.util.JSchemaUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class JSchemaTypeLoader extends TypeLoaderBase {

  private Map<String, IJSchemaType> _rawTypes = new HashMap<String, IJSchemaType>();
  private Map<IFile, List<String>> _filesToTypes = new HashMap<IFile, List<String>>();

  private static final String JSCHEMA_EXT = "jschema";
  private static final String JSON_EXT = "json";
  private boolean _initing;
  private Set<String> _namespaces;

  public boolean handlesNonPrefixLoads() {
    return true;
  }

  private LockingLazyVar<List<JsonFile>> _jscFiles = new LockingLazyVar<List<JsonFile>>() {
    @Override
    protected List<JsonFile> init() {
      return findFilesOfType( JSCHEMA_EXT );
    }
  };

  private LockingLazyVar<List<JsonFile>> _jsonFiles = new LockingLazyVar<List<JsonFile>>() {
    @Override
    protected List<JsonFile> init() {
      return findFilesOfType(JSON_EXT);
    }
  };


  public JSchemaTypeLoader(IModule env) {
    super(env);
  }

  @Override
  public IType getType(String fullyQualifiedName) {
    maybeInitTypes();
    if (fullyQualifiedName == null || _rawTypes.get(fullyQualifiedName) == null) {
      return null;
    }
    IType iType = _rawTypes.get(fullyQualifiedName);
    return TypeSystem.getOrCreateTypeReference(iType);
  }

  @Override
  public Set<? extends CharSequence> getAllNamespaces()
  {
    if (_namespaces == null) {
      try {
        _namespaces = TypeSystem.getNamespacesFromTypeNames(getAllTypeNames(), new HashSet<String>());
      } catch (NullPointerException e) {
        //!! hack to get past dependency issue with tests
        return Collections.emptySet();
      }
    }
    return _namespaces;
  }

  private void maybeInitTypes() {
    if (!_initing) {
      if (_rawTypes.isEmpty()) {
        _initing = true;
        try {
          for (JsonFile jshFile : _jscFiles.get()) {
            try {
              jshFile.parseContent();
              addRootType(_rawTypes, new Stack<Map<String, String>>(), jshFile, jshFile.file, _filesToTypes);
            } catch (Exception e) {
              throw GosuExceptionUtil.forceThrow(e);
            }
          }
          for (JsonFile jsonFile : _jsonFiles.get()) {
            try {
              jsonFile.parseContent();
              convertToJSchemaAndAddRootType(_rawTypes, jsonFile, jsonFile.file, _filesToTypes);
            } catch (Exception e) {
              throw GosuExceptionUtil.forceThrow(e);
            }
          }
          initInnerClasses(_rawTypes);
        } finally {
          _initing = false;
        }
      }
    }
  }

  public String[] getTypesForFile( IFile file ) {
    ArrayList<IType> types = new ArrayList<IType>();
    List<String> typeNamesForFile = getTypeNamesForFile(file);
    for (String s : typeNamesForFile) {
      IType type = TypeSystem.getByFullNameIfValid(s);
      if (type != null) {
        types.add(type);
      }
    }
    return typeNamesForFile.toArray( new String[typeNamesForFile.size()] );
  }

  @Override
  public void refreshedNamespace( String namespace, IDirectory dir, RefreshKind kind )
  {
    if (_namespaces != null) {
      if (kind == RefreshKind.CREATION) {
        _namespaces.add(namespace);
      } else if (kind == RefreshKind.DELETION) {
        _namespaces.remove(namespace);
      }
    }
  }

  @Override
  public boolean hasNamespace( String namespace )
  {
    return getAllNamespaces().contains( namespace );
  }

  @Override
  public Set<String> computeTypeNames()
  {
    maybeInitTypes();
    return _rawTypes.keySet();
  }

  private List<String> getTypeNamesForFile(IFile file) {
    maybeInitTypes();
    List<String> typeNames = _filesToTypes.get(file);
    if (typeNames == null) {
      typeNames = Collections.emptyList();
    }
    return typeNames;
  }

  private void convertToJSchemaAndAddRootType(Map<String, IJSchemaType> rawTypes, JsonFile jsonFile, IFile file, Map<IFile, List<String>> fileMapping) {
    jsonFile.content = JSchemaUtils.convertJsonToJSchema(jsonFile.content);
    addRootType(rawTypes, new Stack<Map<String, String>>(), jsonFile, file, fileMapping);
    return;
  }

  private void initInnerClasses(Map<String, IJSchemaType> rawTypes) {
    for (String name : rawTypes.keySet()) {
      IType iType = rawTypes.get(name);
      IType outerType = rawTypes.get(iType.getNamespace());
      if (outerType instanceof IJSchemaType) {
        ((IJSchemaType) outerType).addInnerClass(iType);
      }
    }
  }

  private void addRootType(Map<String, IJSchemaType> rawTypes, Stack<Map<String, String>> typeDefs, JsonFile jshFile, IFile file, Map<IFile, List<String>> fileMapping) {
    if (jshFile.content instanceof List) {
      int depth = 0;
      while (jshFile.content instanceof List && ((List) jshFile.content).size() > 0) {
        depth++;
        jshFile.content = ((List) jshFile.content).get(0);
      }
      addTypes(rawTypes, typeDefs, jshFile.rootTypeName + ".Element", jshFile.content, file, fileMapping);
      JSchemaListWrapperType rawType = new JSchemaListWrapperType(jshFile.rootTypeName, this, depth, jshFile.content, jshFile.file );
      rawTypes.put(jshFile.rootTypeName, rawType);
      rawType.addErrors(jshFile.errors);
    } else {
      addTypes(rawTypes, typeDefs, jshFile.rootTypeName, jshFile.content, file, fileMapping);
      IJSchemaType rootType = rawTypes.get(jshFile.rootTypeName);
      if (rootType instanceof JSchemaTypeBase) {
        ((JSchemaTypeBase) rootType).addErrors(jshFile.errors);
      }
    }
  }
  
  private void addTypes(Map<String, IJSchemaType> rawTypes, Stack<Map<String, String>> typeDefs, String name, Object o, IFile file, Map<IFile, List<String>> fileMapping) {
    // Handles this "customers" : [{ "name" : "string", "id" : "int"}]
    // i.e. an type def in an array field def
    while (o instanceof List && !((List)o).isEmpty()) {
      o = ((List)o).get(0);
    }
    if (o instanceof Map) {
      Map<Object, Object> jsonMap = (Map<Object, Object>)o;
      if (jsonMap.get(JSchemaUtils.JSCHEMA_ENUM_KEY) != null) {
        putType(rawTypes, name, new JSchemaEnumType(name, this, o, file), file, fileMapping);
      } else if (jsonMap.get("map_of") != null) {
        addTypes(rawTypes, typeDefs, name, jsonMap.get("map_of"), file, fileMapping);
      } else {
        try {
          typeDefs.push(new HashMap<String, String>());
          for (Object key : jsonMap.keySet()) {
            if (!JSchemaUtils.JSCHEMA_TYPEDEFS_KEY.equals(key)) {
              // RECURSION. This will call for every field in the definition. We rely on the if(o instanceof Map) thing up
              // there to cause those calls to be ignored.
              if (key != null) {
                addTypes(rawTypes, typeDefs, name + "." + JSchemaUtils.convertJSONStringToGosuIdentifier(key.toString()), jsonMap.get(key), file, fileMapping);
              }
            }
          }
          putType(rawTypes, name, new JSchemaType(name, this, o, copyTypeDefs(typeDefs), file), file, fileMapping);
        } finally {
          typeDefs.pop();
        }
      }
    }
  }

  private void putType(Map<String, IJSchemaType> rawTypes, String name, IJSchemaType type, IFile file, Map<IFile, List<String>> fileMapping) {
    rawTypes.put(name, type);
    List<String> iTypes = fileMapping.get(file);
    if (iTypes == null) {
      iTypes = new ArrayList<String>();
      fileMapping.put(file, iTypes);
    }
    iTypes.add(type.getName());
  }

  private Map<String, String> copyTypeDefs(Stack<Map<String, String>> typeDefs) {
    HashMap<String, String> allTypeDefs = new HashMap<String, String>();
    for (Map<String, String> typeDef : typeDefs) {
      allTypeDefs.putAll(typeDef);
    }
    return allTypeDefs;
  }

  @Override
  public List<String> getHandledPrefixes() {
    return Collections.emptyList();
  }

  /*
  * Default implementation to handle Gosu 0.9 reqs
  */
  private List<JsonFile> findFilesOfType(String extension) {
    List<JsonFile> init = new java.util.ArrayList<JsonFile>();
    List<Pair<String, IFile>> files = findAllFilesByExtension( getModule(), extension );
    for (Pair<String, IFile> pair : files) {
      JsonFile current = new JsonFile();
      current.file = pair.getSecond();
      String relativeNameAsFile = pair.getFirst();
      int trimmedLength = relativeNameAsFile.length() - extension.length() - 1;
      String typeName = relativeNameAsFile.replace('/', '.').replace('\\', '.').substring(0, trimmedLength);
      if (typeName.indexOf('.') == -1) {
        //TODO ignore?
        throw new RuntimeException("Cannot have Simple JSON Schema definitions in the default package");
      }
      current.rootTypeName = typeName;
      init.add(current);
    }
    return init;
  }

  private static class JsonFile {
    private Object content;
    private String stringContent;
    private String rootTypeName;
    private IFile file;
    private List<JsonParseError> errors;

    @Override
    public String toString() {
      return file.getPath().getPathString();
    }

    public void parseContent() {
      try {
        StringBuilder jsonString = new StringBuilder();
        InputStream src = file.openInputStream();
        Scanner s = new Scanner(src);
        while (s.hasNextLine()) {
          jsonString.append(s.nextLine());
          jsonString.append("\n");
        }
        stringContent = jsonString.toString();
        JSchemaParser parser = new JSchemaParser(stringContent);
        try{
          content = parser.parseJSchema();
        } catch (JsonParseException e) {
          content = parser.getValue();
          if (content == null) {
            content = new JsonMap();
          }
          errors = parser.getErrors();
        } finally {
          s.close();
        }
      } catch (IOException e) {
        content = new JsonMap();
        errors = new ArrayList<JsonParseError>();
        errors.add(new JsonParseError("Unable to open JSON file " + file.getPath().getFileSystemPathString() + ": " + e.getMessage(), 0, 0));
      }
    }
  }

  public static List<Pair<String, IFile>> findAllFilesByExtension( IModule module, String extension ) {
    List<Pair<String, IFile>> results = new ArrayList<>();

    for (IDirectory sourceEntry : module.getSourcePath()) {
      if (sourceEntry.exists()) {
        String prefix = sourceEntry.getName().equals(IModule.CONFIG_RESOURCE_PREFIX) ? IModule.CONFIG_RESOURCE_PREFIX : "";
        addAllLocalResourceFilesByExtensionInternal(module, prefix, sourceEntry, extension, results);
      }
    }
    return results;
  }

  private static void addAllLocalResourceFilesByExtensionInternal( IModule module, String relativePath, IDirectory dir, String extension, List<Pair<String, IFile>> results ) {
    List<IDirectory> excludedPath = Arrays.asList(module.getFileRepository().getExcludedPath());
    if ( excludedPath.contains( dir )) {
      return;
    }
    if(!CommonServices.getPlatformHelper().isPathIgnored(relativePath)) {
      for(IFile file : dir.listFiles()) {
        if(file.getName().endsWith(extension)) {
          String path = appendResourceNameToPath(relativePath, file.getName());
          results.add(new Pair<>(path, file));
        }
      }
      for(IDirectory subdir : dir.listDirs()) {
        String path = appendResourceNameToPath(relativePath, subdir.getName());
        addAllLocalResourceFilesByExtensionInternal(module, path, subdir, extension, results);
      }
    }
  }

  private static String appendResourceNameToPath(String relativePath, String resourceName) {
    String path;
    if(relativePath.length() > 0) {
      path = relativePath + '/' + resourceName;
    } else {
      path = resourceName;
    }
    return path;
  }
}
