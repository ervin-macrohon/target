import sys
import os
from xml.etree.ElementTree import ElementTree as xml


def get_rsl_ver():
    ns = "http://maven.apache.org/POM/4.0.0"
    tree  = xml()
    tree.parse('pom.xml')
    return tree.getroot().find("{%s}version" % ns).text
	
def get_env():
    rsl_jar_name = 'remoteswinglibrary-' + get_rsl_ver() + '.jar'
    webstart_path = os.path.join(os.getcwd(), 'src', 'test', 'robotframework', 'acceptance', 'webstart')
    rsl_path = os.path.join(os.getcwd(), 'target', rsl_jar_name)
    return (webstart_path, rsl_path)
	
def set_env():
    paths = get_env()
    os.environ['PYTHONPATH'] = os.pathsep.join([os.environ.get('PYTHONPATH', ''), paths[0], paths[1]])


if __name__ == '__main__':
    print os.environ.get('PYTHONPATH', '')
    #set_env()