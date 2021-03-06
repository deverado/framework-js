require = {};
if (typeof module === 'undefined') {
  module = {};
}
if (typeof exports === 'undefined') {
  exports = {};
}
{
  var cache = {};
  var filenamesToEntries = {};
  var absPathsToEntries = {};

  var ensureEndswithJs = function(filename) {
    if (!filename.endsWith('.js')) {
      filename = filename + '.js';
    }
    return filename;
  };

  var getFilename = function (path) {
    var p2 = path, lastSlash = path.lastIndexOf('/');
    if (lastSlash >= 0) {
      p2 = path.substring(lastSlash + 1);
    }
    p2 = ensureEndswithJs(p2);
    return p2;
  };

  var getPath = function (path) {
    print ("path before ", path);
    path = new java.io.File(path).getParent();
    path = path || "";
    path = com.google.common.io.Files.simplifyPath(path);
    print("path ", path);
    return path;
    //var p2 = path, lastSlash = path.lastIndexOf('/');
    //if (lastSlash === 0) {
    //  return "/";
    //}
    //if (lastSlash > 0) {
    //  p2 = path.substring(0, lastSlash);
    //}
    // else no slash : only file, path = ""
    //return "";
  };

  var makeCacheablePath = function (path) {
    path = ensureEndswithJs(path);
    if (path.indexOf('.') === 0) {
      path = path.substring(1);
    }
    if (path.indexOf('/') !== 0) {
      path = '/' + path;
    }
    return path;
  };

  var getPropsList = function(obj) {
    var exports = [];
    for (var p in obj) {
      exports.push(p);
    }
    return exports;
  };

  var requireProto = null;

  var loadTargetEntry = function(entry) {
    var previousModule = module;
    var previousExports = exports;
    module = {};
    module.exports = {};
    exports = module.exports;
    cache[getFilename(entry.getKey())] = module.exports;
    cache[makeCacheablePath(entry.getKey())] = module.exports;
    (function () {
      var path = getPath(entry.getKey());
      var require = function(target) {
        print("currying " + target + " with " + path);
        return requireProto(path, target);
      };
      (function(){
        eval(entry.getValue().read());
      })();
      //load({name: entry.getKey(), script: "require('blah');\n" + entry.getValue().read()});
    })();
    if (module.exports !== exports) {
      // exports where reassigned
      cache[getFilename(entry.getKey())] = module.exports;
      cache[makeCacheablePath(entry.getKey())] = module.exports;
    }
    print("stuff: ", entry.getKey(), getPropsList(cache[makeCacheablePath(entry.getKey())]) );
    module = previousModule;
    exports = previousExports;
  };

  requireProto = function (localpath, target) {
    var t2 = target, lastSlash = target.lastIndexOf('/');
    var filename = getFilename(target);
    var targetEndingInJs = ensureEndswithJs(target);

    var absPath;
    // remove leading ./
    if (targetEndingInJs.indexOf('./') === 0) {
      targetEndingInJs = targetEndingInJs.substring(2);
    }
    if (targetEndingInJs.indexOf('/') === 0) {
      absPath = targetEndingInJs;
    } else if (localpath.lastIndexOf('/') === localpath.length - 1) {
      absPath = localpath + targetEndingInJs;
    } else {
      absPath = localpath + '/' + targetEndingInJs;
    }
    absPath = makeCacheablePath(absPath);
    //print("requiring", absPath, " for ", target);

    if (cache[absPath]) {
      //print('returning from ' + cache + ' for ' + target + ' exports: ' + getPropsList(cache[t2]));
      return cache[absPath];
    } else {
      var loaded = false;
      //print('loading into cache for ' + target + ' ' + t2 + ' ' + filenamesToEntries[t2]);
      if (absPathsToEntries[absPath]) {
        loadTargetEntry(absPathsToEntries[absPath]);
        loaded = true;
      }
      if (false && !loaded) {
        if (filenamesToEntries[t2]) {
          loadTargetEntry(filenamesToEntries[t2]);
        }
      }
      if (!loaded) {
        throw "Missing target " + target + " filename " + t2 + " abspath " + absPath + " available: "
            + getPropsList(absPathsToEntries);
      }
      //print('loaded into cache ' + target + " exports: " + getPropsList(cache[t2]));
      return cache[absPath];
    }
  };

  requireProto.__addTargets = function(linkedMap, loadTargetsInMapOrder) {
    var targetIter = linkedMap.entrySet().iterator();
    while (targetIter.hasNext()) {
      var entry = targetIter.next();

      var filename = getFilename(entry.getKey());
      if (filenamesToEntries[filename]) {
        throw "Collision, the fake require uses filenames to identify require modules. " +
          "These two have the same filename: " + entry.getKey() + " and " + filenamesToEntries[filename].getKey();
      }
      filenamesToEntries[filename] = entry;

      var cacheablePath = makeCacheablePath(entry.getKey());
      if (absPathsToEntries[cacheablePath]) {
        throw "Collision, two files with same path: " + entry.getKey();
      }
      absPathsToEntries[cacheablePath] = entry;
      //print('parsing ' + entry.getKey() + ' to ' + getFilename(entry.getKey());
    }

    if (loadTargetsInMapOrder) {
      targetIter = linkedMap.entrySet().iterator();
      while (targetIter.hasNext()) {
        var entry = targetIter.next();
        require(entry.getKey());
      }
    }
  };

  require = function(target) {
    return requireProto("", target);
  };

  require.__addTargets = requireProto.__addTargets;

}
