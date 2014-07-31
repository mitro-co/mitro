// Serves files in the current directory and delay.js.
// To run: go run testwebserver.go
// See: comment in frame_busting_login.html
package main

import (
	"fmt"
	"net/http"
	"strconv"
	"time"
)

// Serves an empty .js file after some delay. Useful for delaying onload
// events (and testing the infobar).
func delayJS(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/javascript;charset=utf-8")
	err := r.ParseForm()
	if err != nil {
		panic("Error: " + err.Error())
	}

	delayMs, err := strconv.Atoi(r.FormValue("ms"))
	if err != nil {
		panic("Error: " + err.Error())
	}

	time.Sleep(time.Duration(delayMs) * time.Millisecond)
}

func main() {
	fmt.Println("Serving at http://localhost:8000/")
	http.HandleFunc("/delay.js", delayJS)
	http.Handle("/", http.FileServer(http.Dir(".")))
	http.ListenAndServe(":8000", nil)
}
