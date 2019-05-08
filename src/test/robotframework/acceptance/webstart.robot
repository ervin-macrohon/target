*** Settings ***
Library    RemoteSwingLibrary        debug=True
Library    FileServer
Library    OperatingSystem
Suite Setup     FileServer.Start
Suite Teardown    Clean Up
Force tags      Webstart

*** Variables ***
${WEBSTART DIR}=    ${CURDIR}/webstart

*** Test Cases ***
Webstart Test
    Start Application    test-app    javaws ${WEBSTART DIR}/test-app/test-application.jnlp    60   close_security_dialogs=True
    [Timeout]    60 seconds
    Set Jemmy Timeouts     15
    Select Main Window
    List Components In Context
    Ensure Application Should Close    5 seconds   Push Button  systemExitButton

*** Keywords ***
 Clean Up
    FileServer.Stop
    Remove Files  *.png
