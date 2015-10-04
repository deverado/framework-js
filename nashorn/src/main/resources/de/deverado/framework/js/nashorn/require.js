require = {};
if (typeof module === 'undefined') {
  module = {};
}
if (typeof exports === 'undefined') {
  exports = {};
}
{
  var cache = {};
  var absPathsToEntries = {};

  var ensureEndswithJs = function(path) {
    if (!path.endsWith('.js')) {
      path = path + '.js';
    }
    return path;
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
    path = new java.io.File(path).getParent();
    path = path || "";
    path = com.google.common.io.Files.simplifyPath(path);
    return path;
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
        //print("currying " + target + " with " + path);
        return requireProto(path, target);
      };
      (function(){
        eval(entry.getValue().read());
        // sad, load doesn't work currently because the require in the closure doesn't propagate to the loaded script
        //load({name: entry.getKey(), script: "require('blah');\n" + entry.getValue().read()});
      })();
    })();
    if (module.exports !== exports) {
      // exports where reassigned
      cache[getFilename(entry.getKey())] = module.exports;
      cache[makeCacheablePath(entry.getKey())] = module.exports;
    }
    module = previousModule;
    exports = previousExports;
  };

  requireProto = function (localpath, target) {
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
    //print("requiring", absPath, " for ", target, " in path ", localpath);

    if (cache[absPath]) {
      //print('returning from ' + cache + ' for ' + target + ' exports: ' + getPropsList(cache[absPath]));
      return cache[absPath];
    } else {
      var loaded = false;
      //print('loading into cache for ' + target + ' ' + absPath + ' ' + absPathsToEntries[absPath]);
      if (absPathsToEntries[absPath]) {
        loadTargetEntry(absPathsToEntries[absPath]);
      } else {
        throw "Missing target " + target + " abspath " + absPath + " available: "
            + getPropsList(absPathsToEntries);
      }
      //print('loaded into cache ' + target + " exports: " + getPropsList(cache[absPath]));
      return cache[absPath];
    }
  };

  requireProto.__addTargets = function(linkedMap, loadTargetsInMapOrder) {
    var targetIter = linkedMap.entrySet().iterator();
    while (targetIter.hasNext()) {
      var entry = targetIter.next();

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
