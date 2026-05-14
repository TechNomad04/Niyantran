package handlers

import (
	"Niyantran/utils"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"os"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
)

type PipelineResult struct {
	Probability  *float64 `json:"probability"`
	HasDryEye    *bool    `json:"has_dry_eye"`
	MeanEarLeft  float64  `json:"mean_ear_left"`
	MeanEarRight float64  `json:"mean_ear_right"`
}

func (h *Handler)UploadInfo(c *gin.Context) {
	file, header, err := c.Request.FormFile("file")
	if err != nil {
		if err.Error() == "http: request body too large" {
			utils.ErrorHandler(c, 413, "File too large", fmt.Sprintf("%v", err))
			return
		}
		utils.ErrorHandler(c, 400, "Bad Request", fmt.Sprintf("%v", err))
		return
	}
	defer file.Close()

	screentime := c.PostForm("screentime")

	userID := c.MustGet("userID").(string)

	parsedUserId, err := strconv.Atoi(userID)

	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H {
			"error" : "Internal server error",
			"msg" : err.Error(),
		})
		return
	}

	var query =  `
		INSERT INTO results (userid, screentime, probability, time) 
		VALUES ($1, $2, 0.0, $3)
		RETURNING id
	`

	var resultID int

	err = h.DB.QueryRow(
		query,
		parsedUserId,
		screentime,
		time.Now(),
	).Scan(&resultID)

	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{
			"error" : "Internal server error",
			"msg" : err.Error(),
		})
		return
	}

	var body bytes.Buffer
	writer := multipart.NewWriter(&body)

	t := time.Now()
	str := t.Format("2006-01-02 15:04:05")
	part, err := writer.CreateFormFile("file", str+" -- "+header.Filename)
	if err != nil {
		utils.ErrorHandler(c, 500, "Internal server error", fmt.Sprintf("%v", err))
		return
	}

	_, err = io.Copy(part, file)
	if err != nil {
		utils.ErrorHandler(c, 500, "Internal server error", fmt.Sprintf("%v", err))
		return
	}

	err = writer.WriteField("resultid", strconv.Itoa(resultID))
	if err != nil {
		return
	}

	writer.Close()

	pipelineURL := os.Getenv("PIPELINE_URL") + "/analyze"
	req, err := http.NewRequest("POST", pipelineURL, &body)
	if err != nil {
		utils.ErrorHandler(c, 500, "Internal server error", fmt.Sprintf("%v", err))
		return
	}

	req.Header.Set("Content-Type", writer.FormDataContentType())

	client := &http.Client{}
	resp, err := client.Do(req)

	if err != nil {
		utils.ErrorHandler(c, 500, "Internal server error", fmt.Sprintf("%v", err))
		return
	}

	defer resp.Body.Close()

	respData, err := io.ReadAll(resp.Body)
	if err != nil {
		utils.ErrorHandler(c, 500, "Internal server error", fmt.Sprintf("%v", err))
		return
	}

	var result PipelineResult
	if err := json.Unmarshal(respData, &result); err == nil && result.Probability != nil {
		// Update the DB row with the real probability from the pipeline
		h.DB.Exec(
			"UPDATE results SET probability = $1 WHERE id = $2",
			*result.Probability, resultID,
		)
	}

	c.JSON(200, gin.H{
		"code":           200,
		"probability":    result.Probability,
		"has_dry_eye":    result.HasDryEye,
		"mean_ear_left":  result.MeanEarLeft,
		"mean_ear_right": result.MeanEarRight,
	})
	c.Abort()
}
