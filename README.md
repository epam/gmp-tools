# Tools module for GMP [![Build Status](https://travis-ci.org/epam/gmp-tools.svg?branch=master)](https://travis-ci.org/epam/gmp-tools)
Integration module for [GMP](https://github.com/epam/GMP) allows to work with Jira, Confluence and ElasticSearch.

TODO documentation.

# Release process
Each push to master branch will trigger [release workflow](.github/workflows/release.yml) with creation of new release with increased patch version 1.0.x.

To increase minor version, need to add label `release:minor` in PR

To increase major version, need to add label `release:major` in PR

If no releases planned, need to add label `norelease` in PR 

All documentation about that process can be found here: [Github Release On Push Action](https://github.com/rymndhng/release-on-push-action#faq)