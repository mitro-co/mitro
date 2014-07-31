package goagent

import (
	"bytes"
	"crypto/tls"
	"encoding/json"
	"errors"
	"io/ioutil"
	"log"
	"net/http"
	"net/url"
	"os/exec"
)

const updateSecretFromAgentPath = "/mitro-core/api/internal/UpdateSecretFromAgent"
const phantomCommand = "phantomjs"

// Top level request object sent to MitroServlet.
type SignedRequest struct {
	ImplicitEndTransaction   *bool  `json:"omitempty"`
	ImplicitBeginTransaction *bool  `json:"omitempty"`
	OperationName            string `json:"operationName,omitempty"`

	// Optional transaction id for this request.
	TransactionId *string `json:"omitempty"`

	// Serialized JSON object containing the actual request. A string is easy to sign/verify.
	Request   string `json:"request"`
	Signature string `json:"signature"`

	// User making the request.
	Identity string `json:"identity"`

	ClientIdentifier string `json:"omitempty"`
	Platform         string `json:"omitempty"`
}

type UpdateSecretFromAgentRequest struct {
	DataFromUser          string `json:"dataFromUser"`
	DataFromUserSignature string `json:"dataFromUserSignature"`

	Username         string `json:"username"`
	URL              string `json:"url"`
	ClientIdentifier string `json:"clientIdentifier"`
	Platform         string `json:"platform"`
}

type CriticalData struct {
	Password    string `json:"password"`
	Note        string `json:"note"`
	OldPassword string `json:"oldPassword"`
}

type UserData struct {
	SecretId     int          `json:"secretId"`
	UserId       string       `json:"userId"`
	CriticalData CriticalData `json:"criticalData"`
}

type UpdateSecretFromAgentResponse struct {
	dataFromUser          string
	dataFromUserSignature string
}

// MitroCoreClient contains settings for communicating with the Mitro core server.
type MitroCoreClient struct {
	client      *http.Client
	internalURL string
}

func NewClientInsecure(internalURL string) (*MitroCoreClient, error) {
	t := &http.Transport{
		TLSClientConfig: &tls.Config{InsecureSkipVerify: true},
	}
	return newClient(internalURL, &http.Client{Transport: t})
}

func NewClient(internalURL string) (*MitroCoreClient, error) {
	return newClient(internalURL, &http.Client{})
}

func newClient(internalURL string, client *http.Client) (*MitroCoreClient, error) {
	u, err := url.Parse(internalURL)
	if err != nil {
		return nil, err
	}
	if u.Scheme != "http" && u.Scheme != "https" {
		return nil, errors.New("unsupported scheme: " + u.Scheme)
	}
	if u.Host == "" {
		return nil, errors.New("internalURL missing host")
	}
	if u.Path != "" || u.Fragment != "" {
		return nil, errors.New("internalURL must not have path or fragment")
	}

	return &MitroCoreClient{client, internalURL}, nil
}

func (c *MitroCoreClient) post(path string, request interface{}, response interface{}) error {
	topLevel := SignedRequest{}
	topLevel.OperationName = "changepw"

	serialized, err := json.Marshal(request)
	if err != nil {
		return err
	}
	topLevel.Request = string(serialized)
	// TODO: sign?
	topLevel.Signature = ""
	topLevel.Identity = "changepw@mitro.co"

	serialized, err = json.Marshal(topLevel)
	if err != nil {
		return err
	}

	postResponse, err := c.client.Post(c.internalURL+path, "application/json", bytes.NewReader(serialized))
	if err != nil {
		return err
	}

	responseBytes, err := ioutil.ReadAll(postResponse.Body)
	postResponse.Body.Close()
	if err != nil {
		return err
	}
	if postResponse.StatusCode != http.StatusOK {
		return errors.New("post failed: " + postResponse.Status)
	}

	return json.Unmarshal(responseBytes, response)
}

func (c *MitroCoreClient) UpdateSecretFromAgent(request *UpdateSecretFromAgentRequest) error {
	response := UpdateSecretFromAgentResponse{}
	return c.post(updateSecretFromAgentPath, request, &response)
}

var supportedSites = map[string]struct{}{
	"github.com":    {},
	"instagram.com": {},
	"skype.com":     {},
	"twitter.com":   {},
}

func ChangePassword(loginURL string, username string, oldPassword string, newPassword string) error {
	u, err := url.Parse(loginURL)
	if err != nil {
		return err
	}
	log.Print("change password for host ", u.Host)
	_, exists := supportedSites[u.Host]
	if !exists {
		return errors.New("Unsupported site: " + u.Host)
	}

	log.Printf("attempting change password with phantomjs for host=%s username=%s", u.Host, username)
	arguments := []string{"changepw.js", u.Host, username, oldPassword, newPassword}
	phantomjs := exec.Command(phantomCommand, arguments...)
	output, err := phantomjs.CombinedOutput()
	if err != nil {
		message := err.Error() + ": " + string(output)
		lastIndex := len(message)
		for message[lastIndex-1] == '\n' {
			lastIndex -= 1
		}
		return errors.New(message[:lastIndex])
	}
	return nil
}
