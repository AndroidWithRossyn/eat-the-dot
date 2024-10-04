@Echo Off
Set "JV="
For /F "Tokens=3" %%A In ('java -version 2^>^&1') Do If Not Defined JV Set "JV=%%~A"
If /I "%JV%"=="not" (Echo Java is not installed&pause) Else start javaw -jar plist-cropper-merger-03-2018.jar
echo "program starting..."
pause