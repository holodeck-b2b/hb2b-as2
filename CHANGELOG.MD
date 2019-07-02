# Change Log
All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).

## 1.2.0
###### 2019-06-11

### Changed
* Adapted to Holodeck B2B 4.2.0: using customised AS2 message builder.

## 1.1.1
###### 2019-06-11

### Fixed
* Issue in parsing the MDN Request options where spaces were included in names and values of MDN options, causing issues
  in and/or creation of the MDN
* Missing P-Mode identifier on generated errors, causing a-sync errors to fail
* NPEs when no preferred digest algorithms where specified for signing the MDN in both `MDNMetadataFactory` and 
  `CreateSignature`
* Incorrect class cast when retrieving signature processing results from processing context in `MDNMetadataFactory` 

## 1.1.0
###### 2019-04-05

### Changed
* Adapted to Holodeck B2B 4.1.0: using new event names and new processing context object.
* Use the same digest algorithm name in the MIME header of MDN as used in the MDN request (when the signature is based
  on the MDN request)  

## 1.0.0
###### 2018-11-04

### Added
* Initial release.
