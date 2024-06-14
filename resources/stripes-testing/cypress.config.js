const { defineConfig } = require('cypress');
const path = require('path');
const globby = require('globby');
const converter = require('json-2-csv');
const { downloadFile } = require('cypress-downloadfile/lib/addPlugin');
const { rmdir, unlink } = require('fs');
const fs = require('fs');
const allureWriter = require('@shelex/cypress-allure-plugin/writer');
const { cloudPlugin } = require('cypress-cloud/plugin');
const registerReportPortalPlugin = require('@reportportal/agent-js-cypress/lib/plugin');

const delay = async ms => new Promise(res => setTimeout(res, ms));

const reportportalOptions = {
  endpoint: 'https://poc-report-portal.ci.folio.org/api/v1',
  apiKey: 'karate_YTzxxZQCTouIhffBDFYf9VFjLPdLn5sSumAN9Fs7SB64EIu3wrPFgbXHPc1OGs0Q',
  launch: 'runNightlyCypressTests1271.109',
//  launchId: '2e63f6f8-b1c3-4fff-ae5d-91395b5086d6',
  //CI_BUILD_ID: '106-1',
  project: 'cypress-nightly',
  description: 'CYPRESS scheduled tests bla bla',
  //autoMerge: true,
  //parallel: true,
  isLaunchMergeRequired: true,
  //reportHooks: true,
  bkbkbkb: false,
  debug: true,
  restClientConfig: {
    timeout: 360000,
  },
  attributes: [
    {
      key: 'build',
      value: '109',
    }
  ],
}

module.exports = defineConfig({
  retries: {
    runMode: 0,
    openMode: 0,
  },
  numTestsKeptInMemory: 1,
  viewportWidth: 1920,
  viewportHeight: 1080,
  video: false,
  defaultCommandTimeout: 51000,
  pageLoadTimeout: 60000,
  downloadsFolder: 'cypress/downloads',
  env: {
    OKAPI_HOST: 'https://folio-testing-cypress-okapi.ci.folio.org',
    OKAPI_TENANT: 'diku',
    diku_login: 'diku_admin',
    diku_password: 'admin',
    is_kiwi_release: false,
    downloadTimeout: 2000,
    allure: true,
    allureReuseAfterSpec: true,
    grepFilterSpecs: true,
    grepOmitFiltered: true,
    rtrAuth: true,
    ecsEnabled: false,
  },
  reporter: '@reportportal/agent-js-cypress',
  reporterOptions: reportportalOptions,
  e2e: {
    async setupNodeEvents(on, config) {
      allureWriter(on, config);

      on('task', {
        log(message) {
          // eslint-disable-next-line no-console
          console.log(message);
          return null;
        },

        async findFiles(mask) {
          if (!mask) {
            throw new Error('Missing a file mask to search');
          }

          const list = await globby(mask);

          if (!list.length) {
            return null;
          }

          return list;
        },

        convertCsvToJson(data) {
          const options = { excelBOM: true, trimHeaderFields: true, trimFieldValues: true };
          return converter.csv2json(data, options);
        },

        downloadFile,

        deleteFolder(folderName) {
          return new Promise((resolve, reject) => {
            // eslint-disable-next-line consistent-return
            rmdir(folderName, { maxRetries: 10, recursive: true }, (err) => {
              if (err && err.code !== 'ENOENT') {
                return reject(err);
              }

              resolve(null);
            });
          });
        },

        deleteFile(pathToFile) {
          return new Promise((resolve, reject) => {
            // eslint-disable-next-line consistent-return
            unlink(pathToFile, (err) => {
              if (err && err.code !== 'ENOENT') {
                return reject(err);
              }

              resolve(null);
            });
          });
        },

        readFileFromDownloads(filename) {
          const downloadsFolder =
            config.downloadsFolder || path.join(__dirname, '..', '..', 'Downloads');
          const filePath = path.join(downloadsFolder, filename);
          return fs.readFileSync(filePath, 'utf-8');
        },
      });

      // keep Cypress running until the ReportPortal reporter is finished. this is a
      // very critical step, as otherwise results might not be completely pushed into
      // ReportPortal, resulting in unfinsihed launches and failing merges
      on('after:run', async (result) => {
        console.log(`after:run event start`);

        if(result){
          //console.log(`after:run Result: ${JSON.stringify(result)}`)

          console.log('Wait for reportportal agent to finish...');

          console.log(`after:run Project root: ${result.config.projectRoot}`);
          console.log(`after:run Current dir: ${process.cwd()}`);

          let files = globby.sync('rplaunchinprogress*.tmp');

          if (files.length > 0) {
            console.log('after:run Print all undeleted files...');
            files.map(file => console.log(file));
            console.log('after:run Printing of all undeleted files finished');
          }

          if (globby.sync('rplaunchinprogress*.tmp').length > 0) {
          //while (globby.sync('rplaunchinprogress*.tmp').length > 0) {
            console.log("Report portal. At least I am here to wait...")
            await delay(2000);
          }
          //console.log('reportportal agent finished');
          //if (reportportalOptions.isLaunchMergeRequired) {
          //  try {
          //    console.log('Merging launches...');
              //await mergeLaunches(reportportalOptions);
          //   console.log('Launches successfully merged!');
          //    //deleteLaunchFiles();
          //  } catch (mergeError) {
          //    console.error(mergeError);
          //  }
          //}
        }
      });

      // fix for cypress-testrail-simple plugin
      if ('TESTRAIL_PROJECTID' in process.env && process.env.TESTRAIL_PROJECTID === '') {
        delete process.env.TESTRAIL_PROJECTID;
      }

      registerReportPortalPlugin(on, config);

      // eslint-disable-next-line global-require
      const grepConfig = require('@cypress/grep/src/plugin')(config);

      const result = await cloudPlugin(on, grepConfig);

      // eslint-disable-next-line global-require
      await require('cypress-testrail-simple/src/plugin')(on, config);

      return result;
    },
    baseUrl: 'https://folio-testing-cypress-diku.ci.folio.org',
    testIsolation: false,
  },
});
