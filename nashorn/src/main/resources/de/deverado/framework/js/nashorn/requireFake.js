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
  var getFilename = function (path) {
    var p2 = path, lastSlash = path.lastIndexOf('/');
    if (lastSlash >= 0) {
      p2 = path.substring(lastSlash + 1);
    }
    if (!p2.endsWith('.js')) {
      p2 = p2 + '.js';
    }
    return p2;
  };

  var getPropsList = function(obj) {
    var exports = [];
    for (var p in obj) {
      exports.push(p);
    }
    return exports;
  };

  var loadTargetEntry = function(entry) {
    var previousModule = module;
    var previousExports = exports;
    module = {};
    module.exports = {};
    exports = module.exports;
    cache[getFilename(entry.getKey())] = module.exports;
    (function () {
      load({name: entry.getKey(), script: entry.getValue().read()});
    })();
    if (module.exports !== exports) {
      // exports where reassigned
      cache[getFilename(entry.getKey())] = module.exports;
    }
    module = previousModule;
    exports = previousExports;
  };

  require = function (target) {
    var t2 = target, lastSlash = target.lastIndexOf('/');
    t2 = getFilename(target);
    if (cache[t2]) {
      //print('returning from ' + cache + ' for ' + target + ' exports: ' + getPropsList(cache[t2]));
      return cache[t2];
    } else {
      //print('loading into cache for ' + target + ' ' + t2 + ' ' + filenamesToEntries[t2]);
      if (!filenamesToEntries[t2]) {
        throw "Missing target " + target + " filename " + t2;
      }
      loadTargetEntry(filenamesToEntries[t2]);
      //print('loaded into cache ' + target + " exports: " + getPropsList(cache[t2]));
      return cache[t2];
    }
  };

  require.__addTargets = function(linkedMap, loadTargetsInMapOrder) {
    var targetIter = linkedMap.entrySet().iterator();
    while (targetIter.hasNext()) {
      var entry = targetIter.next();

      var filename = getFilename(entry.getKey());
      if (filenamesToEntries[filename]) {
        throw "Collision, the fake require uses filenames to identify require modules. " +
          "These two have the same filename: " + entry.getKey() + " and " + filenamesToEntries[filename].getKey();
      }
      filenamesToEntries[filename] = entry;
      //print('parsing ' + entry.getKey() + ' to ' + getFilename(entry.getKey());
    }

    if (loadTargetsInMapOrder) {
      targetIter = linkedMap.entrySet().iterator();
      while (targetIter.hasNext()) {
        var entry = targetIter.next();
        require(entry.getKey());
      }
    }
  }

}
