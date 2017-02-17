set -e
while [[ $# -gt 1 ]]
do
key="$1"

case $key in
    -v|--version)
    VERSION="$2"
    shift # past argument
    ;;
    -u|--user)
    USER="$2"
    shift # past argument
    ;;
    -p|--pass)
    PASS="$2"
    shift # past argument
    ;;
esac
shift # past argument or value
done
if [[ ! -d ~/.datomic/datomic-pro-${VERSION} ]]; then
    mkdir -p ~/.datomic
    set -x
    wget --http-user=${USER} --http-password=${PASS} https://my.datomic.com/repo/com/datomic/datomic-pro/${VERSION}/datomic-pro-${VERSION}.zip -O ~/.datomic/datomic-pro-${VERSION}.zip;
    unzip ~/.datomic/datomic-pro-${VERSION}.zip -d ~/.datomic
    rm ~/.datomic/datomic-pro-${VERSION}.zip;
    cd ~/.datomic/datomic-pro-${VERSION} && sh ./bin/maven-install;
    mvn install:install-file -DgroupId=com.datomic -DartifactId=datomic-transactor-pro -Dfile=datomic-transactor-pro-${VERSION}.jar -DpomFile=pom.xml;
fi
