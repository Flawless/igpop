const vscode = require('vscode');
const os = require('os');
const fs = require('fs');
const http = require('http');
const https = require('https');
const { URL } = require('url');

const releaseDownloader = require('@fohlen/github-release-downloader');

let installDeclined = false;

export function promptForInstall() {
  if (installDeclined) {
    return;
  }
  vscode.window.showInformationMessage('Igpop executable is missing from your $PATH or \'igpop.path\' is not set to the correct path. Would you like to install it?', 'Install')
    .then((selection) => {
      if (selection === 'Install') {
        install();
      } else {
        installDeclined = true;
      }
    });
}

async function install() {
  console.log('Getting latest release for your platform...');
  let url;
  try {
    url = await getDownloadUrl();
    console.log(`Found latest release: ${url}`);
    if (url === null || url === undefined || url.toString().trim() === '') {
      console.log('Could not find the latest release for your platform');
      return;
    }
  } catch (e) {
    console.log('Something went wrong while getting the latest release:');
    console.log(e);
    return;
  }

  console.log('Downloading executable...');
  let path;
  try {
    path = await downloadFile(url);
    console.log(`igpop executable downloaded to ${path}`);
  } catch (e) {
    console.log('Something went wrong while downloading the executable:');
    console.log(e);
    return;
  }

  console.log('Changing file mode to 0755 to allow execution...');
  try {
    await vscode.workspace.getConfiguration('igpop').update('path', path, true);
    fs.chmodSync(path, 0o755);
  } catch (e) {
    console.log(e);
    return;
  }

  console.log(`Setting 'igpop.path' to '${path}'...`);
  try {
    await vscode.workspace.getConfiguration('igpop').update('path', path, true);
  } catch (e) {
    console.log('Something went wrong while saving the \'igpop.path\' setting:');
    console.log(e);
    return;
  }
  console.log('Done!');
}

// Downloads a file given a URL
// Returns a Promise that resolves to the absolute file path when the download is complete
async function downloadFile(url) {
  // Use the user's home directory as the default base directory
  const dest = os.homedir();
  return releaseDownloader.downloadAsset(url.href, getExecutableName(), dest);
}

async function getDownloadUrl() {
  return new Promise((resolve, reject) => {
    // TODO: Honor HTTP proxy settings from `vscode.workspace.getConfiguration('http').get('proxy')`
    https.get({
      hostname:'api.github.com',
      path: '/repos/HealthSamurai/igpop/releases/latest',
      headers: {
        'User-Agent': 'node.js',
        'Authorization': `token ${getToken()}`
      }
    }, res => {
      let rawData = '';
      res.on('data', d => {
        rawData += d;
      });
      res.on('end', () => {
        try {
          const release = JSON.parse(rawData);
          const url = getUrlFromRelease(release);
          resolve(new URL(url));
        } catch (e) {
          reject(e);
        }
      });
    }).on('error', e => reject(e));
  });
}

function getUrlFromRelease(release) {
  // release.assets.name contains {'darwin', 'linux', 'windows'}
  const assets = release.assets || [];
  const os = process.platform;
  let targetAsset;
  switch (os) {
  case 'darwin':
    targetAsset = assets.filter((asset) => asset.name.indexOf('darwin') !== -1)[0];
    break;
  case 'linux':
    targetAsset = assets.filter((asset) => asset.name.indexOf('linux') !== -1)[0];
    break;
  case 'win32':
    targetAsset = assets.filter((asset) => asset.name.indexOf('windows') !== -1)[0];
    break;
  default:
    targetAsset = {browser_download_url: ''};
  }
  return targetAsset.browser_download_url;
}

function getExecutableName() {
  const os = process.platform;
  switch (os) {
  case 'darwin':
  case 'linux':
    return 'igpop';
  case 'win32':
    //    return 'igpop.exe';
  default:
    return 'igpop';
  }
}

function getToken() {};
