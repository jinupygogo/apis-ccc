# apis-ccc

## Introduction


## Installation
```bash
$ git clone https://github.com/SonyCSL/apis-bom.git
$ cd apis-bom
$ mvn install
$ cd ../
$ git clone https://github.com/SonyCSL/apis-common.git
$ cd apis-common
$ mvn install
$ cd ../
$ git cone https://github.com/SonyCSL/apis-ccc.git
$ cd apis-ccc
$ mvn package
$ cd ../
$ mkdir apis-ccc_exe
$ cp ./apis-ccc/target/apis-ccc-*-fat.jar ./apis-ccc_exe
$ cp ./apis-ccc/setting_files/* ./apis-ccc_exe
```

## Parameter Setting
Set the following file parameters in the apis-ccc_exe at least to suit your environment.   
Refer to "Chapter 7, About Configuration Files" in the [apis-ccc_specification](#anchor1) for more information.

&emsp;config.json   
&emsp;&emsp;&emsp;- communityId   &emsp;(default : oss_communityId)  
&emsp;&emsp;&emsp;- clusterId     &emsp;(default : oss_clusterId)  

&emsp;cluster.xml  
&emsp;&emsp;&emsp;- \<interface\>  &emsp;(default : 127.0.0.1)

&emsp;start.sh  
&emsp;&emsp;&emsp;- java arguments &emsp;(default : 127.0.0.1) 


## Running

```bash
$ cd apis-ccc_exe
$ bash start.sh
```

<a id="anchor1"></a>
## Documentation



## License
&emsp;[Apache License Version 2.0](https://github.com/SonyCSL/apis-ccc/blob/master/LICENSE)


## Notice
&emsp;[Notice](https://github.com/SonyCSL/apis-ccc/blob/master/NOTICE.md)
