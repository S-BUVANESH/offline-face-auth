const fs = require('fs');
const https = require('https');
const http = require('http');
const { URL } = require('url');

const dest = 'app/src/main/assets/mobilefacenet.tflite';

// Array of candidate URLs
const urls = [
    'https://raw.githubusercontent.com/siriusmin/MobileFaceNet-Android/master/app/src/main/assets/mobilefacenet.tflite',
    'https://raw.githubusercontent.com/siriusmin/MobileFaceNet-Android/main/app/src/main/assets/mobilefacenet.tflite',
    'https://raw.githubusercontent.com/v-shujun/FaceRecognition-MobileFaceNet-Android/master/app/src/main/assets/mobilefacenet.tflite',
    'https://raw.githubusercontent.com/shubham0204/Face_Recognition_with_Tensorflow_Lite/master/app/src/main/assets/mobilefacenet.tflite',
    'https://gitee.com/galeorando/MobileFaceNet-Android/raw/master/app/src/main/assets/mobilefacenet.tflite'
];

function download(urlStr, resolve, reject) {
    console.log(`\nConnecting to: ${urlStr}`);
    const u = new URL(urlStr);
    const client = u.protocol === 'https:' ? https : http;

    const request = client.get(urlStr, (res) => {
        console.log(`Status Code: ${res.statusCode}`);
        console.log(`Headers: ${JSON.stringify(res.headers)}`);

        if (res.statusCode === 301 || res.statusCode === 302 || res.statusCode === 307 || res.statusCode === 308) {
            const redirectUrl = res.headers.location;
            console.log(`Following redirect to: ${redirectUrl}`);
            download(redirectUrl, resolve, reject);
            return;
        }

        if (res.statusCode !== 200) {
            reject(new Error(`Failed with status code ${res.statusCode}`));
            return;
        }

        const len = parseInt(res.headers['content-length'] || '0', 10);
        console.log(`Content-Length: ${len} bytes`);

        const file = fs.createWriteStream(dest);
        let downloaded = 0;

        res.on('data', (chunk) => {
            file.write(chunk);
            downloaded += chunk.length;
            if (len > 0) {
                const percent = ((downloaded / len) * 100).toFixed(1);
                process.stdout.write(`Progress: ${downloaded}/${len} bytes (${percent}%)\r`);
            } else {
                process.stdout.write(`Progress: ${downloaded} bytes\r`);
            }
        });

        res.on('end', () => {
            file.end();
            console.log(`\nSuccessfully downloaded ${downloaded} bytes!`);
            if (downloaded > 1000000) {
                console.log('Valid TFLite file check passed!');
                resolve(true);
            } else {
                reject(new Error(`Downloaded file too small: ${downloaded} bytes`));
            }
        });
    });

    request.on('error', (err) => {
        console.error(`Network Error: ${err.message}`);
        reject(err);
    });

    request.setTimeout(10000, () => {
        console.log('Timeout hit. Aborting request.');
        request.destroy();
        reject(new Error('Timeout'));
    });
}

function runSequence(index) {
    if (index >= urls.length) {
        console.log('\nAll download candidates exhausted.');
        process.exit(1);
    }

    new Promise((resolve, reject) => {
        download(urls[index], resolve, reject);
    }).then(() => {
        console.log('\nAsset download sequence successful!');
        process.exit(0);
    }).catch((err) => {
        console.log(`Candidate ${index} failed: ${err.message}`);
        runSequence(index + 1);
    });
}

runSequence(0);
