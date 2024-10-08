# Change Log
All notable changes to this project will be documented in this file.
This project adheres to [Semantic Versioning](http://semver.org/).

## 4.0.0
##### 2024-09-02
### Changed
* Based on Holodeck B2B 7.0.0

## 3.0.1
##### 2023-06-29
### Fixed
* No Receipt sent when parallel delivery is used

## 3.0.0
##### 2023-03-08 
### Changed
* Based on Holodeck B2B 6.0.0

### Fixed
* Preliminary termination of MDN message on Java 11 [#10](https://github.com/holodeck-b2b/hb2b-as2/issues/10)
* Missing AS2-Version HTTP header [#7](https://github.com/holodeck-b2b/hb2b-as2/issues/7)
* HTTP headers are not checked case insensitively [#8](https://github.com/holodeck-b2b/hb2b-as2/issues/8) and 
  [#9](https://github.com/holodeck-b2b/hb2b-as2/issues/9)

## 2.1.0
##### 2021-12-14

### Changed
* Refactored code to use the generic utility classes from https://github.com/holodeck-b2b/generic-utils
* Based on Holodeck B2B 5.3.1, uses new SUSPENDED processing state when an error occurs during send process

## 2.0.0
##### 2020-09-03 

### Added
* Detailed disposition text for errors [#3](https://github.com/holodeck-b2b/hb2b-as2/issues/3)

### Changed
* Adapted to Holodeck B2B 5.0.0: 
	- Added a customised AS2 message builder
	- Using the installed _Certificate Manager_ to validate trust of signing certificate
	- Use `IMessageProcessingContext` instead of implementation class
	- Changed logging class of handlers to Log4j

### Fixed
* Failed validation of direct trust (as this is now handled by the _Certificate Manager_)
* Responder not correctly evaluated in P-Mode finding [#4](https://github.com/holodeck-b2b/hb2b-as2/issues/4)
* Negative MDN does not contain AS2-To and AS2-From HTTP headers [#5](https://github.com/holodeck-b2b/hb2b-as2/issues/5)

## 1.1.1
##### 2019-06-11

### Fixed
* Issue in parsing the MDN Request options where spaces were included in names and values of MDN options, causing issues
  in and/or creation of the MDN
* Missing P-Mode identifier on generated errors, causing a-sync errors to fail
* NPEs when no preferred digest algorithms where specified for signing the MDN in both `MDNMetadataFactory` and 
  `CreateSignature`
* Incorrect class cast when retrieving signature processing results from processing context in `MDNMetadataFactory` 

## 1.1.0
##### 2019-04-05

### Changed
* Adapted to Holodeck B2B 4.1.0: using new event names and new processing context object.
* Use the same digest algorithm name in the MIME header of MDN as used in the MDN request (when the signature is based
  on the MDN request)  

## 1.0.0
###### 2018-11-04

### Added
* Initial release.
