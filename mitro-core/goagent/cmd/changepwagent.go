package main

import (
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"log"
	"net/http"
	"net/url"

	"github.com/evanj/gohttpsserver"
	"github.com/mitro-co/mitro-core/goagent"
)

// Wraps an http handler function to return 500 and a message on errors.
type errorHandler struct {
	handleWithError func(http.ResponseWriter, *http.Request) error
}

func (handler *errorHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	err := handler.handleWithError(w, r)
	if err != nil {
		log.Print("Returning error from handler: ", err.Error())
		http.Error(w, "Error: "+err.Error(), http.StatusInternalServerError)
	}
}

type ChangePasswordAgent struct {
	client         *goagent.MitroCoreClient
	changePassword func(loginURL string, username string, oldPassword string, newPassword string) error
}

func fakeChangePassword(loginURL string, username string, oldPassword string, newPassword string) error {
	log.Println("fake change password", loginURL, username, oldPassword, newPassword)
	return nil
}

func (c *ChangePasswordAgent) request(w http.ResponseWriter, r *http.Request) error {
	log.Print("request starting")

	// Read and parse the request
	decoder := json.NewDecoder(r.Body)
	request := goagent.UpdateSecretFromAgentRequest{}
	err := decoder.Decode(&request)
	if err != nil {
		return err
	}
	err = r.Body.Close()
	if err != nil {
		return err
	}

	log.Print("decoded request:", request)

	// validate the structure
	userData := goagent.UserData{}
	err = json.Unmarshal([]byte(request.DataFromUser), &userData)
	if err != nil {
		return err
	}
	log.Print("decoded UserData:", userData)
	if request.URL == "" {
		return errors.New("Missing URL")
	}
	if request.DataFromUserSignature == "" || request.URL == "" ||
		userData.UserId == "" || userData.CriticalData.OldPassword == "" || userData.CriticalData.Password == "" {
		return errors.New("Invalid request")
	}
	log.Printf("request UserId=%s URL=%s ClientIdentifier=%s Platform=%s",
		userData.UserId, request.URL, request.ClientIdentifier, request.Platform)

	// looks okay: let's actually try to run the thing
	err = c.changePassword(request.URL, request.Username, userData.CriticalData.OldPassword,
		userData.CriticalData.Password)
	if err != nil {
		return err
	}

	// we changed the password! Update Mitro
	err = c.client.UpdateSecretFromAgent(&request)
	if err != nil {
		return err
	}

	response := goagent.UpdateSecretFromAgentResponse{}
	serialized, err := json.Marshal(response)
	if err != nil {
		return err
	}
	w.Header().Set("Content-Type", "application/json")
	_, err = w.Write(serialized)
	if err != nil {
		return err
	}
	return nil
}

func serveHTTPSProxy(target *url.URL, port int) {
	// Hoping this would trust all localhost subdomains, but it doesn't work
	// could be replaced with gohttpsserver.ServeWithNewSelfSigned()
	certHosts := []string{"localhost", "127.0.0.1"}
	certificate, err := gohttpsserver.NewSelfSignedCertificate(certHosts)

	proxy := gohttpsserver.NewSingleHostReverseProxy(target)
	err = gohttpsserver.Serve(fmt.Sprintf(":%d", port), certificate, proxy)
	if err != nil {
		log.Fatal("failed to serve: ", err)
	}
}

func main() {
	useLocalServer := flag.Bool("useLocalServer", false, "Uses localhost server if true")
	skipChangePasswords := flag.Bool("skipChangePasswords", false,
		"Does not change passwords on the real service if true")
	httpsPort := flag.Int("httpsPort", 8444, "Port number to listen on")
	httpPort := flag.Int("httpPort", 8445, "Port number to listen on")
	flag.Parse()

	var client *goagent.MitroCoreClient
	var err error
	if *useLocalServer {
		log.Print("WARNING: using insecure local server (error if not testing)")
		client, err = goagent.NewClientInsecure("https://localhost:8443")
	} else {
		client, err = goagent.NewClient("https://www.mitro.co")
	}
	if err != nil {
		panic(err.Error())
	}
	changePassword := goagent.ChangePassword
	if *skipChangePasswords {
		log.Print("WARNING: not actually changing passwords (error if not testing)")
		changePassword = fakeChangePassword
	}

	server := ChangePasswordAgent{client, changePassword}
	http.Handle("/ChangePassword", &errorHandler{server.request})

	httpAddr := fmt.Sprintf(":%d", *httpPort)
	u, err := url.Parse("http://localhost" + httpAddr)
	if err != nil {
		panic(err.Error())
	}
	go serveHTTPSProxy(u, *httpsPort)
	log.Printf("serving on https://localhost:%d", *httpsPort)
	log.Printf("serving on http://localhost%s", httpAddr)
	err = http.ListenAndServe(httpAddr, nil)
	if err != nil {
		panic(err.Error())
	}
}
