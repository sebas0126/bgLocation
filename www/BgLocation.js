var exec = require('cordova/exec');

exports.initLocation = function (token, userId, device, url, schedule, distance, time, success, error) {
    exec(success, error, 'BgLocation', 'coolMethod', [token, userId, device, url, schedule, distance, time]);
};
