var exec = require('cordova/exec');

exports.initLocation = function (token, userId, device, url, schedule, distance, time, success, error) {
    exec(success, error, 'BgLocation', 'initLocation', [token, userId, device, url, schedule, distance, time]);
};
