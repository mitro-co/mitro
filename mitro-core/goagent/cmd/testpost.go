package main

import (
	"encoding/json"
	"flag"
	"fmt"

	"github.com/mitro-co/mitro-core/goagent"
)

func panicIfError(err error) {
	if err != nil {
		panic(err.Error())
	}
}

func main() {
	useLocalServer := flag.Bool("useLocalServer", false, "Uses localhost server if true")
	flag.Parse()

	var client *goagent.MitroCoreClient
	var err error
	if *useLocalServer {
		client, err = goagent.NewClientInsecure("https://localhost:8443")
	} else {
		client, err = goagent.NewClient("https://www-internal.mitro.co")
	}
	panicIfError(err)

	// build a request manually
	data := goagent.UserData{}
	data.UserId = "ej2@lectorius.com"
	data.SecretId = 18
	data.CriticalData.Password = "newpw"
	data.CriticalData.OldPassword = "oldpw"
	data.CriticalData.Note = "note"
	serialized, err := json.Marshal(data)
	panicIfError(err)
	// TODO: Add signature so this can actually get executed correctly?
	request := goagent.UpdateSecretFromAgentRequest{string(serialized), ""}

	err = client.UpdateSecretFromAgent(&request)
	panicIfError(err)
	fmt.Println("SUCCESS? (I think)")
}
