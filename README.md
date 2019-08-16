# Holodeck B2B AS2
This project contains an extension for Holodeck B2B that adds support for the AS2 message exchange protocol to the Holodeck B2B Core. It is meant to enable an easy migration from AS2 to AS4 using just one gateway. Using this extension, switching from AS2 to AS4 is simply a matter of updating the P-Mode of the message exchange to be migrated. 

## Features
* P-Mode based configuration
* Message signing with support for newer algorithms like SHA-256
* Message encryption with support for newer algorithms like AES128-GCM
* Message compression
* Synchronous and asynchronous MDNs

__________________
For more information on using Holodeck B2B visit http://holodeck-b2b.org  
Lead developer: Sander Fieten  
Code hosted at https://github.com/holodeck-b2b/hb2b-as2  
Issue tracker https://github.com/holodeck-b2b/hb2b-as2/issues  

## Installation
### Prerequisites  
This extension requires that you have already deployed Holodeck B2B version 4.2.0 or later. 
You will also need the Bouncy Castle security libraries for processing S/MIME. Download these libraries from Maven Central and copy them to the `lib` directory of the Holodeck B2B instance. The libraries you download *must* have the same version number as the one already installed in the Holodeck B2B instance (check the `bcprov-jdk15on` file). You need to install both the [bcmail](http://repo2.maven.org/maven2/org/bouncycastle/bcmail-jdk15on) and [bcpkix](http://repo2.maven.org/maven2/org/bouncycastle/bcpkix-jdk15on) library.

### Set up  
To add this extension to a Holodeck B2B instance follow these three steps. Please note that you cannot install the extension in a running Holodeck B2B instance.
1. Build the extension or download the latest release package. You should now have the `holodeck-as2-«version».jar` and `holodeck-as2-msh.aar` files available.
2. Copy the jar file to the `lib` directory of the Holodeck B2B instance and copy the aar file to the `repository/services` directory.
3. In `conf/holodeckb2b.xml` change the value for the _PModeValidator_ parameter to _org.holodeckb2b.as2.PModeValidator_. Note that this parameter is not used by default and you therefore may need to uncomment this element.

### Messaging Configuration
To configure AS2 message exchanges the Holodeck B2B Core P-Modes are used. As the messaging model of AS2 is very similar to AS4 most settings in the P-Mode can be directly mapped to an AS2 message exchange, although some parameters might use different values for AS2.   
The main differences [in the messaging model] between AS2 and AS4 are that AS2 does not use the _Service_ and _Action_ meta-data and uses the MDN to represent both a positive and negative acknowledgement for a received message whereas in AS4 these are represented separately by the Receipt and Error Signals.  
Another important difference is the configuration of when to send a response message. For AS4 this is solely configured in the P-Mode, but in AS2 the sender of the message can also request a MDN and provide some configuration options. This extension supports both and will use the P-Mode settings if provided.  
The sections below explain the AS2 specific P-Mode settings.

#### Common settings for both sending and receiving
To indicate that a P-Mode configures an AS2 message exchange the MEP Binding parameter must be set _http://holodeck-b2b.org/pmode/mepBinding/as2_.

As AS2 is always a push the sender and receiver of the AS2 message are configured in using the _Initiator_ respectively _Responder_ P-Mode parameters. There should be only one identifier without type attribute per trading partner.

Signing and encryption of the messages is configured the same way as for AS4 message exchanges but with the following changes:
* **Signing/KeyReferenceMethod** : can be used to indicate whether the certificate used for signing must be included with the signature. If this is required use value _BSTReference_.
* **Signing/Algorithm** : the value for this parameter should be taken from the list of supported [cryptographic algorithms](supported_crypto_algorithms.md).
* **Signing/HashFunction** : the hash function is derived from the signing algorithm and cannot be specified separately. This parameter is now used to indicate which digest naming format should be used in the messages. By default the names as specified in RFC3851 are used as this RFC is referenced by the AS2 specification. Set this parameter to _RFC5751_ to use the naming format from that RFC.
* **Encryption/KeyTransport** : The only setting in this group applicable to AS2 message exchanges is the **KeyReferenceMethod** which can have value _IssuerSerial_ or _KeyIdentifier_ to specify how the key used for encryption should be identified. The default value is _IssuerSerial_.
* **Encryption/Algorithm** : like the signing algorithm the value for this parameter should be taken from the list of supported [cryptographic algorithms](supported_crypto_algorithms.md).

#### Settings for sending
When configuring a P-Mode for sending of AS2 messages the following parameters are used in a different way than for AS4:
* **Receipt** : To request a asynchronous MDN from the receiver of the sent message add the _To_ setting to the Receipt configuration and specify the URL where the receiver should send the MDN to (probably the URL where Holodeck B2B receives the AS2 messages).
* **Service** and **Action** : Although AS2 doesn't support the Service and Action meta-data they must be available when the message is submitted to Holodeck B2B. It is therefore recommended to set these parameters in the P-Mode.
* **UseAS4Compression** : This parameter is also used to enable AS2 message compression.

#### Settings receiving
There are no AS2 specific settings for receiving messages. Just note that when the P-Mode is used, this overrides the response parameters as included in the MDN request from the sender included with the received message. This means that when a Receipt or Error response is specified in the P-Mode there will always be an MDN (for errors this of course depends on whether an MDN can be created). If the P-Mode specifies that Receipts or Errors must be reported asynchronously this will also override the MDN request. If you specify synchronous responses the MDN request can override this to asynchronous.

When an MDN is received this is always transformed into a Receipt or Error Signal. The information included in the MDN is included as XML in the Receipt and Error messages. When using the default Holodeck B2B delivery methods this means that the MDN info is only available for Error Signals as the Receipt content is not included in the delivery.

## Contributing
We are using the simplified Github workflow to accept modifications which means you should:
* create an issue related to the problem you want to fix or the function you want to add (good for traceability and cross-reference)
* fork the repository
* create a branch (optionally with the reference to the issue in the name)
* write your code
* commit incrementally with readable and detailed commit messages
* submit a pull-request against the master branch of this repository

If your contribution is more than a patch, please contact us beforehand to discuss which branch you can best submit the pull request to.

### Submitting bugs
You can report issues directly on the [project Issue Tracker](https://github.com/holodeck-b2b/hb2b-as2/issues).  
Please document the steps to reproduce your problem in as much detail as you can (if needed and possible include screenshots).

## Versioning
Version numbering follows the [Semantic versioning](http://semver.org/) approach.

## License
The Holodeck B2B AS2 extension is licensed under the General Public License V3 (GPLv3) which is included in the LICENSE file in the root of the project.
This means you are not allowed to integrate Holodeck B2B AS2 in a closed source product. You can however use Holodeck B2B AS2 together with your closed source product as long as you only use the interfaces (API's) in the [Core project|https://github.com/holodeck-b2b/Holodeck-B2B] to communicate with Holodeck B2B. For this purpose, the interfaces module is licensed under the Lesser General Public License V3 (LGPLv3).

## Support
Commercial Holodeck B2B support is provided by Chasquis. Visit [Chasquis-Consulting.com](http://chasquis-consulting.com/holodeck-b2b-support/) for more information.
